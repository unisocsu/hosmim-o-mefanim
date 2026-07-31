const { app, BrowserWindow, Menu, shell } = require('electron');
const path = require('path');
const fs = require('fs');

// המשחק מנגן מוזיקה מיד עם לחיצת "התחל" — בלי חסימת autoplay
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

// מופע יחיד — שני עותקים היו נלחמים על אותו localStorage (שיאים/העדפות)
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    const w = BrowserWindow.getAllWindows()[0];
    if (w) { if (w.isMinimized()) w.restore(); w.focus(); }
  });
}

// זכירת מיקום/גודל חלון בין הרצות — התנהגות דסקטופ סטנדרטית
const statePath = () => path.join(app.getPath('userData'), 'window-state.json');
function loadWinState() {
  try { return JSON.parse(fs.readFileSync(statePath(), 'utf8')); } catch (e) { return null; }
}
function saveWinState(win) {
  try {
    const b = win.getNormalBounds();
    fs.writeFileSync(statePath(), JSON.stringify({
      x: b.x, y: b.y, width: b.width, height: b.height,
      max: win.isMaximized(), full: win.isFullScreen()
    }));
  } catch (e) {}
}

function createWindow() {
  const st = loadWinState();
  const win = new BrowserWindow({
    width: st ? st.width : 1280,
    height: st ? st.height : 800,
    ...(st && Number.isFinite(st.x) ? { x: st.x, y: st.y } : {}),
    minWidth: 800,
    minHeight: 600,
    title: 'חוסמים את הציר',
    icon: path.join(__dirname, 'app', 'icon-512.png'),
    autoHideMenuBar: true,
    backgroundColor: '#120d24',
    webPreferences: { contextIsolation: true, sandbox: true }
  });
  Menu.setApplicationMenu(null);

  // קישורים חיצוניים (mailto במדיניות הפרטיות) — החוצה לדפדפן, לא בתוך המשחק
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^(https?|mailto):/.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });

  // F11 — מסך מלא
  win.webContents.on('before-input-event', (e, input) => {
    if (input.key === 'F11' && input.type === 'keyDown') {
      win.setFullScreen(!win.isFullScreen());
      e.preventDefault();
    }
  });

  win.on('close', () => saveWinState(win));

  win.loadFile(path.join(__dirname, 'app', 'index.html'));
  if (!st || st.max) win.maximize();
  if (st && st.full) win.setFullScreen(true);
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
