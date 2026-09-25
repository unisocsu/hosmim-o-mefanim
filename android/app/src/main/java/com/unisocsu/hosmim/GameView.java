package com.unisocsu.hosmim;

import android.content.Context;
import android.graphics.*;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GameView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(11);
    private final List<Unit> units = new ArrayList<Unit>();
    private final List<Unit> enemies = new ArrayList<Unit>();
    private final List<Car> cars = new ArrayList<Car>();
    private final ArrayList<Unit> selected = new ArrayList<Unit>();

    private static final String[] MAP_NAMES = {"צומת בר אילן", "כביש 4", "גשר המיתרים"};
    private static final int[] MAP_LENGTH = {220, 280, 240};
    private static final float[] ROAD_HALF = {4.2f, 7.2f, 5.8f};

    private int map = 0;
    private int side = 0;
    private int score = 0;
    private int wave = 0;
    private int stage = 0;
    private float gameTime = 0f;
    private float jamPeak = 0f;
    private float tickerTime = 0f;
    private String ticker = "מבזק: מתיחות בצומת...";
    private int water = 100;
    private boolean lying = false;
    private boolean toolsOpen = false;
    private float cameraX;
    private float lastX, lastY;
    private boolean dragging;
    private boolean selecting;
    private boolean menu = true;
    private boolean paused;
    private boolean mouseDrag;
    private Unit draggedUnit;
    private final RectF selectedRect = new RectF();
    private long lastTime;

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        buildMenuScene();
    }

    private void buildMenuScene() {
        units.clear();
        enemies.clear();
        cars.clear();
        for (int i = 0; i < 10; i++) {
            Unit u = makeUnit(Type.BOCHUR, -18 + i * 4, 6 + (i % 3) * 1.7f);
            units.add(u);
        }
        for (int i = 0; i < 7; i++) cars.add(new Car(-80 + i * 28, i % 2 == 0 ? 1 : -1));
    }

    private Unit makeUnit(Type type, float x, float z) {
        Unit u = new Unit();
        u.type = type;
        u.x = x;
        u.z = z;
        u.targetX = x;
        u.targetZ = z;
        u.speed = type == Type.AVRECH ? 1.65f : type == Type.ASKAN ? 2.0f : 2.45f;
        return u;
    }

    private void startGame(int selectedMap) {
        map = Math.max(0, Math.min(2, selectedMap));
        menu = false;
        paused = false;
        score = side == 1 ? 500 : 0;
        wave = 0;
        stage = 0;
        gameTime = 0f;
        jamPeak = 0f;
        tickerTime = 0f;
        ticker = side == 0 ? "מבזק: מתיחות בצומת..." : "מבזק: המשטרה נערכת לפינוי...";
        water = 100;
        lying = false;
        toolsOpen = false;
        cameraX = 0;
        units.clear();
        enemies.clear();
        cars.clear();
        selected.clear();

        if (side == 0) {
            Type[] opening = {
                    Type.BOCHUR, Type.BOCHUR, Type.BOCHUR, Type.BOCHUR,
                    Type.BOCHUR, Type.BOCHUR, Type.AVRECH, Type.AVRECH,
                    Type.AVRECH, Type.ASKAN
            };
            for (int i = 0; i < opening.length; i++) {
                float z = -1.8f + (i / 6) * 1.6f;
                units.add(makeUnit(opening[i], -18 + (i % 6) * 4, z));
            }
        } else {
            for (int i = 0; i < 5; i++)
                enemies.add(makeUnit(Type.BOCHUR, -18 + i * 4, -1.8f + (i % 3) * 1.6f));
        }

        for (int i = 0; i < 12; i++)
            cars.add(new Car(-MAP_LENGTH[map] / 2f - 20 + i * 24, i % 2 == 0 ? 1 : -1));

        lastTime = System.nanoTime();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0 : Math.min(0.05f, (now - lastTime) / 1000000000f);
        lastTime = now;

        if (!menu && !paused) update(dt);
        drawWorld(canvas);
        if (menu) drawMenu(canvas);
        else drawHud(canvas);

        postInvalidateDelayed(16);
    }

    private void update(float dt) {
        gameTime += dt;
        tickerTime += dt;
        if (tickerTime > 5f) {
            tickerTime = 0f;
            if (side == 0) {
                String[] news = {"מבזק: הנהגים מתעכבים...", "דיווח: עוד מפגינים בדרך", "מבזק: הצומת הולך ונסתמין"};
                ticker = news[(int)(gameTime / 5f) % news.length];
            } else {
                String[] news = {"מבזק: הכוחות מתקדמים", "דיווח: הפגנה מתרחבת", "מבזק: המפקד דורש תוצאות"};
                ticker = news[(int)(gameTime / 5f) % news.length];
            }
        }

        if (side == 0) {
            for (Unit u : units) moveToward(u, dt);
            if (score > 250 && enemies.size() < 4) enemies.add(makeUnit(Type.POLICE, 15, -2));
            if (score > 600 && enemies.size() < 8) enemies.add(makeUnit(Type.POLICE, 30, 2));
        } else {
            wave = Math.min(5, 1 + (int)(score / 220));
            if (wave > 1 && enemies.size() < 5 + wave * 2)
                enemies.add(makeUnit(Type.BOCHUR, 20 + random.nextInt(35), -2 + random.nextInt(5)));
            for (Unit u : units) moveToward(u, dt);
            for (Unit u : enemies) moveToward(u, dt);
        }

        for (Car car : cars) {
            car.x += car.dir * car.speed * dt;
            float edge = MAP_LENGTH[map] / 2f + 25;
            if (car.x > edge) car.x = -edge;
            if (car.x < -edge) car.x = edge;
        }

        int blocked = 0;
        for (Car car : cars) if (nearBlocker(car)) blocked++;
        float jam = blocked * 0.35f;
        jamPeak = Math.max(jamPeak, jam);
        if (side == 0) score += Math.round(blocked * 10 * dt);
        else score += Math.round(Math.max(0, 5 - enemies.size()) * dt);

        stage = Math.min(3, (int)(gameTime / 45f));
        wave = Math.min(6, 1 + stage + (int)(gameTime / 30f));
        if (side == 0 && water < 100) water = Math.min(100, water + (int)(12 * dt));
        if (side == 1 && water > 0 && stage >= 2) water = Math.max(0, water - (int)(3 * dt));
    }

    private void moveToward(Unit u, float dt) {
        float dx = u.targetX - u.x;
        float dz = u.targetZ - u.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        if (d > 0.08f) {
            u.x += dx / d * u.speed * dt;
            u.z += dz / d * u.speed * dt;
        }
    }

    private boolean nearBlocker(Car car) {
        for (Unit u : units)
            if (u.type != Type.POLICE && Math.abs(u.x - car.x) < 3.2f && Math.abs(u.z) < ROAD_HALF[map]) return true;
        return false;
    }

    private void drawRoundRectCompat(Canvas c, float left, float top, float right, float bottom, float rx, float ry, Paint paint) {
        Path path = new Path();
        path.addRoundRect(new RectF(left, top, right, bottom), rx, ry, Path.Direction.CW);
        c.drawPath(path, paint);
    }

    private void drawWorld(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(19, 22, 29));
        c.drawRect(0, 0, w, h, p);

        // City blocks.
        p.setColor(Color.rgb(42, 45, 52));
        c.drawRect(0, h * .08f, w, h * .30f, p);
        c.drawRect(0, h * .77f, w, h, p);
        drawBuildings(c, h * .08f, h * .30f, false);
        drawBuildings(c, h * .77f, h, true);

        float top = h * .31f;
        float bottom = h * .76f;
        p.setColor(Color.rgb(70, 72, 76));
        c.drawRect(0, top, w, bottom, p);

        p.setColor(Color.rgb(168, 164, 148));
        c.drawRect(0, top - 13, w, top, p);
        c.drawRect(0, bottom, w, bottom + 13, p);

        p.setColor(Color.rgb(215, 181, 69));
        p.setStrokeWidth(4);
        float center = (top + bottom) / 2f;
        for (int x = -40; x < w + 40; x += 48)
            c.drawLine(x, center, x + 24, center, p);

        float sx = w / (float) MAP_LENGTH[map];
        for (Car car : cars) drawCar(c, car, sx, center);
        for (Unit u : units) drawUnit(c, u, sx, top, bottom, false);
        for (Unit u : enemies) drawUnit(c, u, sx, top, bottom, u.type == Type.POLICE);

        if (selecting) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x2835E08A);
            c.drawRect(selectedRect, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(Color.rgb(53, 224, 138));
            c.drawRect(selectedRect, p);
            p.setStyle(Paint.Style.FILL);
        }

        drawMiniMap(c);
    }

    private void drawBuildings(Canvas c, float top, float bottom, boolean lower) {
        int count = 7;
        float gap = getWidth() / (float) count;
        for (int i = 0; i < count; i++) {
            float x = i * gap + 6;
            float bw = gap - 12;
            p.setColor((i % 2 == 0) ? Color.rgb(58, 61, 70) : Color.rgb(51, 54, 62));
            c.drawRect(x, top + 10, x + bw, bottom - 8, p);
            p.setColor(Color.rgb(224, 190, 82));
            for (int row = 0; row < 3; row++)
                for (int col = 0; col < 2; col++)
                    c.drawRect(x + 12 + col * (bw / 2f), top + 24 + row * 30,
                            x + 20 + col * (bw / 2f), top + 34 + row * 30, p);
        }
    }

    private void drawCar(Canvas c, Car car, float sx, float center) {
        float x = getWidth() / 2f + (car.x - cameraX) * sx;
        float y = center + car.dir * 58;
        p.setColor(car.dir > 0 ? Color.rgb(64, 124, 181) : Color.rgb(188, 76, 66));
        drawRoundRectCompat(c, x - 20, y - 10, x + 20, y + 10, 5, 5, p);
        p.setColor(Color.rgb(215, 230, 238));
        c.drawRect(x - 9, y - 7, x + 9, y + 5, p);
        p.setColor(Color.rgb(35, 38, 43));
        c.drawCircle(x - 13, y + 9, 4, p);
        c.drawCircle(x + 13, y + 9, 4, p);
    }

    private float screenX(float worldX, float sx) {
        return getWidth() / 2f + (worldX - cameraX) * sx;
    }

    private float screenY(float z, float top, float bottom) {
        return (top + bottom) / 2f + z * 28;
    }

    private void drawUnit(Canvas c, Unit u, float sx, float top, float bottom, boolean enemy) {
        float x = screenX(u.x, sx);
        float y = screenY(u.z, top, bottom);
        int body;

        if (enemy || u.type == Type.POLICE) body = Color.rgb(55, 84, 145);
        else if (u.type == Type.AVRECH) body = Color.rgb(105, 72, 46);
        else if (u.type == Type.ASKAN) body = Color.rgb(86, 118, 73);
        else body = Color.rgb(46, 100, 70);

        p.setColor(Color.rgb(225, 185, 145));
        c.drawCircle(x, y - 12, 7, p);
        p.setColor(body);
        drawRoundRectCompat(c, x - 8, y - 5, x + 8, y + 14, 3, 3, p);
        p.setColor(Color.DKGRAY);
        c.drawRect(x - 7, y + 14, x - 2, y + 24, p);
        c.drawRect(x + 2, y + 14, x + 7, y + 24, p);

        if (selected.contains(u)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(enemy ? Color.CYAN : Color.YELLOW);
            c.drawCircle(x, y, 20, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawHud(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setColor(0xE0070A12);
        c.drawRect(0, 0, w, 92, p);
        p.setColor(Color.rgb(145, 27, 27));
        c.drawRect(0, 0, w, 30, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(13);
        p.setColor(Color.WHITE);
        c.drawText("🔴  " + ticker, w / 2f, 20, p);

        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(14);
        p.setColor(Color.WHITE);
        c.drawText(side == 0 ? "צד המפגינים" : "צד המשטרה", 12, 48, p);
        p.setTextSize(23);
        p.setColor(Color.rgb(255, 217, 102));
        c.drawText((side == 0 ? "נקודות: " : "תקציב: ") + score, 12, 76, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(16);
        p.setColor(Color.rgb(154, 208, 255));
        String stageName = stage == 0 ? "התארגנות" : stage == 1 ? "הסלמה" : stage == 2 ? "עימות" : "שעת מבחן";
        c.drawText(MAPS_LABEL(map) + " • " + stageName, w / 2f, 52, p);
        p.setTextSize(12);
        p.setColor(Color.LTGRAY);
        c.drawText(formatTime(gameTime) + "  •  פקק: " + String.format(java.util.Locale.US, "%.1f", jamPeak) + " ק״מ  •  נבחרו: " + selected.size(), w / 2f, 74, p);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(24);
        p.setColor(Color.WHITE);
        c.drawText(paused ? "מושהה" : "⏸", w - 12, 55, p);

        if (side == 1 && stage >= 2) {
            p.setTextSize(11);
            p.setColor(Color.rgb(130, 205, 255));
            c.drawText("מיכל מים " + water + "%", w - 12, 77, p);
        }

        drawToolbar(c);
    }

    private void drawToolbar(Canvas c) {
        int w = getWidth(), h = getHeight();
        float y = h - 58;
        // Keep only the two always-useful actions visible on small screens.
        // במסכים קטנים מציגים רק את כפתור השכיבה, כדי להשאיר את המשחק נקי.
        boolean compact = w < 600 || h < 600;
        if (compact) {
            float l = w / 2f - 48;
            drawToolbarButton(c, l, y, l + 96, y + 48, "שכיבה", lying);
        } else {
            drawToolbarButton(c, 10, y, 96, y + 48, side == 0 ? "כלים" : "פיקוד", toolsOpen);
            drawToolbarButton(c, 104, y, 190, y + 48, "שכיבה", lying);
            drawToolbarButton(c, w - 96, y, w - 10, y + 48, "סיום", false);
        }
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(11);
        p.setColor(0xCCFFFFFF);
        c.drawText("גרור לבחירה • גרור יחידה • קליק בכביש = יעד • גרור רקע = מצלמה", w / 2f, y - 7, p);

        if (toolsOpen) {
            float panelL = 10;
            float panelR = w - 10;
            float panelB = y - 10;
            float panelT = Math.max(105, panelB - 210);
            p.setColor(0xF514111C);
            drawRoundRectCompat(c, panelL, panelT, panelR, panelB, 12, 12, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(14);
            p.setColor(Color.WHITE);
            c.drawText(side == 0 ? "כלים למפגינים" : "כלי פיקוד", (panelL + panelR) / 2f, panelT + 23, p);

            String[] labels = side == 0
                    ? new String[]{"תגבורת", "ותיק", "עסקן", "כלי נוסף"}
                    : new String[]{"ניידת", "מגפון", "מכת״ז", "גרר"};
            String[] costs = side == 0
                    ? new String[]{"50", "150", "250", "500"}
                    : new String[]{"500", "250", "1500", "600"};

            float gap = 8;
            float bw = (panelR - panelL - gap * 3) / 4f;
            float by = panelT + 48;
            for (int i = 0; i < 4; i++) {
                float l = panelL + i * (bw + gap);
                p.setColor(0xFF292330);
                drawRoundRectCompat(c, l, by, l + bw, by + 72, 9, 9, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1);
                p.setColor(0x66888888);
                drawRoundRectCompat(c, l, by, l + bw, by + 72, 9, 9, p);
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(12);
                p.setColor(Color.WHITE);
                c.drawText(labels[i], l + bw / 2f, by + 28, p);
                p.setTextSize(10);
                p.setColor(Color.rgb(255, 217, 102));
                c.drawText(costs[i], l + bw / 2f, by + 49, p);
            }
        }
    }

    private void drawToolbarButton(Canvas c, float l, float t, float r, float b, String label, boolean active) {
        p.setColor(active ? Color.rgb(78, 55, 105) : 0xE60C0A12);
        drawRoundRectCompat(c, l, t, r, b, 9, 9, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0x77FFFFFF);
        drawRoundRectCompat(c, l, t, r, b, 9, 9, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(12);
        p.setColor(Color.WHITE);
        c.drawText(label, (l + r) / 2f, t + 29, p);
    }

    private String MAPS_LABEL(int i) {
        return MAP_NAMES[Math.max(0, Math.min(MAP_NAMES.length - 1, i))];
    }

    private String formatTime(float seconds) {
        int s = (int) seconds;
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }

    private void drawMiniMap(Canvas c) {
        int w = getWidth();
        float size = Math.min(145, w * .25f);
        float left = w - size - 10;
        float top = 86;
        p.setColor(0xC0100D16);
        drawRoundRectCompat(c, left, top, left + size, top + size * .48f, 7, 7, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(0x66FFFFFF);
        drawRoundRectCompat(c, left, top, left + size, top + size * .48f, 7, 7, p);
        p.setStyle(Paint.Style.FILL);

        float roadTop = top + size * .13f;
        float roadBottom = top + size * .35f;
        p.setColor(Color.rgb(73, 75, 80));
        c.drawRect(left, roadTop, left + size, roadBottom, p);

        float sx = size / MAP_LENGTH[map];
        for (Car car : cars) {
            p.setColor(Color.LTGRAY);
            float x = left + size / 2f + car.x * sx;
            c.drawRect(x - 2, (roadTop + roadBottom) / 2 - 1, x + 2, (roadTop + roadBottom) / 2 + 1, p);
        }
        for (Unit u : units) {
            p.setColor(selected.contains(u) ? Color.YELLOW : Color.WHITE);
            float x = left + size / 2f + u.x * sx;
            float y = (roadTop + roadBottom) / 2f + u.z * 2.2f;
            c.drawCircle(x, y, 2, p);
        }
    }

    private void drawMenu(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setColor(0xE8171221);
        c.drawRect(0, 0, w, h, p);

        p.setColor(0xFFEFE5CD);
        drawRoundRectCompat(c, w * .06f, h * .08f, w * .94f, h * .91f, 10, 10, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.rgb(22, 19, 16));
        p.setTypeface(Typeface.create("serif", Typeface.BOLD));
        p.setTextSize(Math.min(48, w / 9f));
        c.drawText("חוסמים את הציר", w / 2f, h * .19f, p);
        p.setTypeface(Typeface.DEFAULT);

        p.setTextSize(16);
        c.drawText("סימולטור אסטרטגיה • גרסה טבעית לאנדרואיד", w / 2f, h * .25f, p);

        button(c, w * .12f, h * .31f, w * .43f, h * .40f,
                "🎩 צד המפגינים", side == 0);
        button(c, w * .47f, h * .31f, w * .88f, h * .40f,
                "🚔 צד המשטרה", side == 1);

        p.setTextSize(15);
        p.setColor(Color.DKGRAY);
        c.drawText("בחר מפה", w / 2f, h * .46f, p);
        for (int i = 0; i < 3; i++) {
            float l = w * (.09f + i * .30f);
            button(c, l, h * .49f, l + w * .25f, h * .58f, MAP_NAMES[i], map == i);
        }

        button(c, w * .24f, h * .64f, w * .76f, h * .74f,
                side == 0 ? "התחל הפגנה" : "פתח במבצע פינוי", true);
        button(c, w * .24f, h * .77f, w * .76f, h * .84f,
                "⚙ הגדרות", false);

        p.setTextSize(12);
        p.setColor(Color.DKGRAY);
        c.drawText("עכבר מערכת ומגע נתמכים • Java בלבד • API 19+", w / 2f, h * .88f, p);
    }

    private void button(Canvas c, float l, float t, float r, float b, String text, boolean active) {
        p.setColor(active ? Color.rgb(32, 28, 23) : Color.rgb(220, 211, 188));
        drawRoundRectCompat(c, l, t, r, b, 5, 5, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(Math.max(13, Math.min(19, getWidth() / 30f)));
        p.setColor(active ? Color.rgb(245, 236, 213) : Color.rgb(38, 34, 29));
        c.drawText(text, (l + r) / 2f, (t + b) / 2f + 7, p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            lastX = x;
            lastY = y;
            dragging = false;
            draggedUnit = null;
            mouseDrag = isMouseLike(e);

            if (menu) {
                float h = getHeight(), w = getWidth();
                if (y > h * .30f && y < h * .42f) {
                    side = x < w / 2f ? 0 : 1;
                    invalidate();
                    return true;
                }
                if (y > h * .48f && y < h * .61f) {
                    for (int i = 0; i < 3; i++) {
                        float l = w * (.09f + i * .30f);
                        if (x >= l && x <= l + w * .25f) {
                            map = i;
                            invalidate();
                            return true;
                        }
                    }
                }
                if (y > h * .63f && y < h * .76f) {
                    startGame(map);
                    return true;
                }
                if (y > h * .76f && y < h * .88f) {
                    ((MainActivity) getContext()).showGameSettings();
                    return true;
                }
                return true;
            }

            if (y < 34) {
                paused = !paused;
                invalidate();
                return true;
            }
            if (toolsOpen) {
                handleToolbarClick(x, y);
                invalidate();
                return true;
            }
            if (y > getHeight() - 68) {
                handleToolbarClick(x, y);
                invalidate();
                return true;
            }

            Unit hit = findUnitAt(x, y);
            if (hit != null && selected.contains(hit)) {
                draggedUnit = hit;
                return true;
            }
            if (hit != null) {
                selected.clear();
                selected.add(hit);
                return true;
            }

            if (y > getHeight() * .29f && y < getHeight() * .79f) {
                selecting = true;
                selectedRect.set(x, y, x, y);
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (draggedUnit != null) {
                float sx = getWidth() / (float) MAP_LENGTH[map];
                draggedUnit.targetX += (x - lastX) / sx;
                draggedUnit.targetZ += (y - lastY) / 28f;
                draggedUnit.x = draggedUnit.targetX;
                draggedUnit.z = draggedUnit.targetZ;
                dragging = true;
            } else if (selecting) {
                selectedRect.right = x;
                selectedRect.bottom = y;
                dragging = true;
            } else {
                float dx = x - lastX;
                if (Math.abs(dx) > 1.5f) {
                    cameraX -= dx * 0.12f;
                    dragging = true;
                }
            }
            lastX = x;
            lastY = y;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (draggedUnit != null) {
                draggedUnit = null;
            } else if (selecting) {
                selecting = false;
                selectUnits(selectedRect);
            } else if (!dragging) {
                commandAt(x, y);
            }
        }

        invalidate();
        return true;
    }

    private void handleToolbarClick(float x, float y) {
        float barY = getHeight() - 58;
        float w = getWidth();

        // בסרגל קומפקטי במסך קטן קיים רק כפתור שכיבה.
        boolean compact = w < 600 || getHeight() < 600;
        if (y >= barY) {
            if (compact) {
                if (x >= w / 2f - 55 && x <= w / 2f + 55) {
                    lying = !lying;
                    ticker = lying ? "המפגינים נשכבו על הכביש" : "המפגינים קמו";
                }
                return;
            }
            if (x <= 100) {
                toolsOpen = !toolsOpen;
                return;
            }
            if (x <= 200) {
                lying = !lying;
                ticker = lying ? "המפגינים נשכבו על הכביש" : "המפגינים קמו";
                return;
            }
            if (x >= w - 105) {
                paused = true;
                menu = true;
                toolsOpen = false;
                buildMenuScene();
                return;
            }
        }

        // Tool panel. It is intentionally checked before generic world commands.
        if (toolsOpen) {
            float panelL = 10;
            float panelR = w - 10;
            float panelB = barY - 10;
            float panelT = Math.max(105, panelB - 210);
            if (x >= panelL && x <= panelR && y >= panelT && y <= panelB) {
                float gap = 8;
                float bw = (panelR - panelL - gap * 3) / 4f;
                float by = panelT + 48;
                if (y >= by && y <= by + 72) {
                    int i = Math.max(0, Math.min(3, (int)((x - panelL) / (bw + gap))));
                    if (side == 0) {
                        if (i == 0 && score >= 50) { score -= 50; addReinforcements(1); }
                        else if (i == 1 && score >= 150) { score -= 150; addVeteran(); }
                        else if (i == 2 && score >= 250) { score -= 250; addAskan(); }
                        else if (i == 3 && score >= 500) { score -= 500; addReinforcements(3); }
                    } else {
                        if (i == 0 && score >= 500) { score -= 500; addPolice(2); }
                        else if (i == 1 && score >= 250) {
                            score -= 250;
                            for (Unit u : enemies) u.targetX += 6;
                            ticker = "המגפון הופעל — הכוחות מתקדמים!";
                        } else if (i == 2 && score >= 1500 && water >= 30) {
                            score -= 1500; water -= 30; enemies.clear(); ticker = "הפעולה בוצעה!";
                        } else if (i == 3 && score >= 600 && !enemies.isEmpty()) {
                            score -= 600; enemies.remove(0); ticker = "הציר התפנה חלקית";
                        }
                    }
                    toolsOpen = false;
                    return;
                }
                return;
            }
            // Tap outside the panel closes it.
            toolsOpen = false;
        }
    }

    private void addReinforcements(int count) {
        for (int i = 0; i < count; i++) units.add(makeUnit(Type.BOCHUR, cameraX + 8 + i * 2, -1.5f + i * 1.2f));
        ticker = "תגבורת נכנסה לשטח!";
    }

    private void addVeteran() {
        units.add(makeUnit(Type.AVRECH, cameraX + 8, 0));
        ticker = "אברך ותיק הוצב בציר";
    }

    private void addAskan() {
        units.add(makeUnit(Type.ASKAN, cameraX + 8, 1.2f));
        ticker = "העסקן נכנס למשא ומתן";
    }

    private void addPolice(int count) {
        for (int i = 0; i < count; i++) enemies.add(makeUnit(Type.POLICE, cameraX + 10 + i * 2, -2));
        ticker = "ניידת הגיעה עם כוחות";
    }

    private Unit findUnitAt(float x, float y) {
        float sx = getWidth() / (float) MAP_LENGTH[map];
        List<Unit> list = side == 0 ? units : enemies;
        for (Unit u : list) {
            if (Math.abs(screenX(u.x, sx) - x) < 25 &&
                    Math.abs(screenY(u.z, getHeight() * .31f, getHeight() * .76f) - y) < 30)
                return u;
        }
        return null;
    }

    private void selectUnits(RectF r) {
        selected.clear();
        float sx = getWidth() / (float) MAP_LENGTH[map];
        float l = Math.min(r.left, r.right);
        float rr = Math.max(r.left, r.right);
        float t = Math.min(r.top, r.bottom);
        float b = Math.max(r.top, r.bottom);
        for (Unit u : side == 0 ? units : enemies) {
            float x = screenX(u.x, sx);
            float y = screenY(u.z, getHeight() * .31f, getHeight() * .76f);
            if (new RectF(l, t, rr, b).contains(x, y)) selected.add(u);
        }
    }

    private void commandAt(float x, float y) {
        if (y > getHeight() - 58) {
            menu = true;
            buildMenuScene();
            return;
        }

        float sx = getWidth() / (float) MAP_LENGTH[map];
        float wx = (x - getWidth() / 2f) / sx + cameraX;
        float wz = (y - (getHeight() * .31f + getHeight() * .76f) / 2f) / 28f;

        if (!selected.isEmpty()) {
            int i = 0;
            for (Unit u : selected) {
                u.targetX = wx + (i % 3 - 1) * 2.6f;
                u.targetZ = wz + (i / 3) * 1.7f;
                i++;
            }
        }
    }

    private boolean isMouseLike(MotionEvent e) {
        int source = e.getSource();
        return (source & InputDevice.SOURCE_CLASS_POINTER) != 0 &&
                (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
    }

    private enum Type { BOCHUR, AVRECH, ASKAN, POLICE }

    private static final class Unit {
        Type type;
        float x, z, targetX, targetZ, speed;
    }

    private static final class Car {
        float x, dir, speed = 18;
        Car(float x, float dir) { this.x = x; this.dir = dir; }
    }
}
