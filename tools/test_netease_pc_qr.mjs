#!/usr/bin/env node

import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, resolve } from "node:path";

const LOGIN_URL = "https://music.163.com/";
const DESKTOP_USER_AGENT =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
  "AppleWebKit/537.36 (KHTML, like Gecko) " +
  "Chrome/124.0.0.0 Safari/537.36";
const DEFAULT_CHROME =
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const outputPath = resolve(process.argv[2] ?? "build/netease-login-qr.png");
const chromePath = process.env.CHROME_PATH ?? DEFAULT_CHROME;

class CdpClient {
  constructor(url) {
    this.nextId = 1;
    this.pending = new Map();
    this.socket = new WebSocket(url);
  }

  async connect() {
    await new Promise((resolveConnection, rejectConnection) => {
      this.socket.addEventListener("open", resolveConnection, { once: true });
      this.socket.addEventListener("error", rejectConnection, { once: true });
    });
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolveCommand, rejectCommand) => {
      this.pending.set(id, { resolveCommand, rejectCommand });
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  handleMessage(event) {
    const message = JSON.parse(event.data);
    if (!message.id) return;
    const pendingCommand = this.pending.get(message.id);
    if (!pendingCommand) return;
    this.pending.delete(message.id);
    if (message.error) {
      pendingCommand.rejectCommand(new Error(message.error.message));
    } else {
      pendingCommand.resolveCommand(message.result);
    }
  }

  startReceiving() {
    this.socket.addEventListener("message", (event) => this.handleMessage(event));
  }

  close() {
    this.socket.close();
  }
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

async function waitForFile(path, timeoutMilliseconds) {
  const deadline = Date.now() + timeoutMilliseconds;
  while (Date.now() < deadline) {
    try {
      return await readFile(path, "utf8");
    } catch {
      await delay(100);
    }
  }
  throw new Error(`Timed out waiting for ${basename(path)}`);
}

async function evaluate(client, expression, awaitPromise = false) {
  const result = await client.send("Runtime.evaluate", {
    expression,
    awaitPromise,
    returnByValue: true,
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text ?? "JavaScript evaluation failed");
  }
  return result.result.value;
}

async function waitForStableNeteasePage(client) {
  const deadline = Date.now() + 20_000;
  let readyChecks = 0;
  while (Date.now() < deadline) {
    try {
      const pageState = await evaluate(
        client,
        `({
          host: location.host,
          protocol: location.protocol,
          readyState: document.readyState
        })`,
      );
      const ready =
        pageState.host === "music.163.com" &&
        pageState.protocol === "https:" &&
        pageState.readyState === "complete";
      readyChecks = ready ? readyChecks + 1 : 0;
      if (readyChecks >= 2) return;
    } catch (error) {
      if (!error.message.includes("Execution context was destroyed")) throw error;
      readyChecks = 0;
    }
    await delay(500);
  }
  throw new Error("Timed out waiting for the NetEase Music page to become stable");
}

const AUTO_OPEN_LOGIN_EXPRESSION = String.raw`
new Promise((resolve) => {
  let attemptsRemaining = 60;

  function isVisible(element) {
    if (!element) return false;
    const style = window.getComputedStyle(element);
    const bounds = element.getBoundingClientRect();
    return style.display !== "none" &&
      style.visibility !== "hidden" &&
      bounds.width > 0 && bounds.height > 0;
  }

  function findLoginButton() {
    const preferredSelectors = [
      '.m-tophead [data-action="login"]',
      '.m-tophead a.link',
      '[data-action="login"]'
    ];
    for (const selector of preferredSelectors) {
      for (const candidate of document.querySelectorAll(selector)) {
        if (isVisible(candidate) && candidate.textContent.trim() === "登录") {
          return candidate;
        }
      }
    }
    return Array.from(document.querySelectorAll("a, button")).find((candidate) => {
      const bounds = candidate.getBoundingClientRect();
      return bounds.top < 160 &&
        isVisible(candidate) &&
        candidate.textContent.trim() === "登录";
    });
  }

  function tryOpenLogin() {
    const loginButton = findLoginButton();
    if (loginButton) {
      loginButton.click();
      resolve(true);
      return;
    }
    attemptsRemaining -= 1;
    if (attemptsRemaining <= 0) {
      resolve(false);
      return;
    }
    window.setTimeout(tryOpenLogin, 250);
  }

  tryOpenLogin();
})`;

const FIND_QR_BOUNDS_EXPRESSION = String.raw`
(() => {
  function isVisible(element) {
    const style = window.getComputedStyle(element);
    const bounds = element.getBoundingClientRect();
    return style.display !== "none" &&
      style.visibility !== "hidden" &&
      Number(style.opacity) > 0 &&
      bounds.width >= 100 && bounds.height >= 100;
  }

  function hasQrContext(element) {
    let current = element;
    for (let depth = 0; current && depth < 8; depth += 1) {
      if ((current.textContent ?? "").includes("扫码登录")) return true;
      current = current.parentElement;
    }
    return false;
  }

  const candidates = [];
  for (const element of document.querySelectorAll("img, canvas, div, span")) {
    if (!isVisible(element)) continue;
    const bounds = element.getBoundingClientRect();
    if (Math.abs(bounds.width - bounds.height) > 12) continue;
    if (bounds.width > 420 || bounds.height > 420) continue;

    const style = window.getComputedStyle(element);
    const source = element.currentSrc || element.src || style.backgroundImage || "";
    const hasRenderedImage =
      (element.tagName === "IMG" && Boolean(element.currentSrc || element.src)) ||
      (element.tagName === "CANVAS" && element.width > 0 && element.height > 0) ||
      ((element.tagName === "DIV" || element.tagName === "SPAN") &&
        style.backgroundImage !== "none");
    if (!hasRenderedImage) continue;
    const identity = [
      element.id,
      typeof element.className === "string" ? element.className : "",
      source
    ].join(" ").toLowerCase();
    let score = 0;
    if (hasQrContext(element)) score += 100;
    if (identity.includes("qr")) score += 80;
    if (element.tagName === "CANVAS") score += 20;
    score += Math.max(0, 30 - Math.abs(bounds.width - 200) / 5);
    score += Math.max(0, 20 - Math.abs(bounds.left + bounds.width / 2 - innerWidth / 2) / 20);
    candidates.push({
      x: bounds.left + scrollX,
      y: bounds.top + scrollY,
      width: bounds.width,
      height: bounds.height,
      score,
      tag: element.tagName,
      source: source.slice(0, 160)
    });
  }
  candidates.sort((left, right) => right.score - left.score);
  return candidates[0] ?? null;
})()`;

async function waitForQrBounds(client) {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const candidate = await evaluate(client, FIND_QR_BOUNDS_EXPRESSION);
    if (candidate && candidate.score >= 100) return candidate;
    await delay(250);
  }
  throw new Error("Login dialog opened, but no QR image was found");
}

async function main() {
  const profileDirectory = await mkdtemp(`${tmpdir()}/netease-pc-qr-`);
  const devToolsPortFile = `${profileDirectory}/DevToolsActivePort`;
  const chrome = spawn(
    chromePath,
    [
      "--headless=new",
      "--disable-gpu",
      "--no-first-run",
      "--no-default-browser-check",
      "--remote-debugging-port=0",
      `--user-data-dir=${profileDirectory}`,
      "--window-size=1440,1200",
      `--user-agent=${DESKTOP_USER_AGENT}`,
      LOGIN_URL,
    ],
    { stdio: "ignore" },
  );

  let client;
  try {
    const portFile = await waitForFile(devToolsPortFile, 10_000);
    const port = portFile.split(/\r?\n/, 1)[0];
    const targets = await fetch(`http://127.0.0.1:${port}/json/list`).then((response) =>
      response.json(),
    );
    const page = targets.find(
      (target) => target.type === "page" && target.url.startsWith(LOGIN_URL),
    );
    if (!page) throw new Error("Could not find the NetEase Music page target");

    client = new CdpClient(page.webSocketDebuggerUrl);
    await client.connect();
    client.startReceiving();
    await client.send("Page.enable");
    await client.send("Runtime.enable");
    await waitForStableNeteasePage(client);

    const opened = await evaluate(client, AUTO_OPEN_LOGIN_EXPRESSION, true);
    if (!opened) throw new Error("Could not find the desktop site's login button");
    const qrBounds = await waitForQrBounds(client);
    const screenshot = await client.send("Page.captureScreenshot", {
      format: "png",
      clip: {
        x: qrBounds.x,
        y: qrBounds.y,
        width: qrBounds.width,
        height: qrBounds.height,
        scale: 1,
      },
      captureBeyondViewport: true,
    });
    await writeFile(outputPath, Buffer.from(screenshot.data, "base64"));
    process.stdout.write(
      `${JSON.stringify({ outputPath, qrBounds }, null, 2)}\n`,
    );
  } finally {
    client?.close();
    chrome.kill("SIGTERM");
    await rm(profileDirectory, { recursive: true, force: true });
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack ?? error}\n`);
  process.exitCode = 1;
});
