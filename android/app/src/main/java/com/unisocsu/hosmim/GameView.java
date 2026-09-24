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
    private final Random random = new Random(7);
    private final List<Unit> people = new ArrayList<Unit>();
    private final List<Unit> police = new ArrayList<Unit>();
    private final List<Car> cars = new ArrayList<Car>();

    private int stage = 0;
    private int side = 0;
    private int score = 0;
    private int wave = 0;
    private float lastX, lastY;
    private boolean dragging;
    private boolean menu = true;
    private boolean paused;
    private long lastTime;
    private float cameraX, cameraY;
    private final RectF selectedRect = new RectF();
    private boolean selecting;
    private Unit draggedUnit;
    private boolean pointerDrag;
    private final ArrayList<Unit> selected = new ArrayList<Unit>();

    private static final int[] MAP_LENGTH = {200, 240, 200};
    private static final float[] ROAD_HALF = {3.7f, 7.2f, 6.0f};

    public GameView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        p.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        startMenuWorld();
    }

    private void startMenuWorld() {
        people.clear(); police.clear(); cars.clear();
        for (int i = 0; i < 14; i++) people.add(makeUnit(0, 50 + i * 2, (i % 5) - 2));
        for (int i = 0; i < 8; i++) cars.add(new Car(20 + i * 25, i % 2 == 0 ? -1 : 1));
    }

    private Unit makeUnit(int team, float x, float z) {
        Unit u = new Unit();
        u.team = team; u.x = x; u.z = z;
        u.targetX = x; u.targetZ = z;
        u.speed = team == 0 ? 2.2f : 2.8f;
        return u;
    }

    private void beginStage(int s) {
        stage = Math.max(0, Math.min(2, s));
        menu = false; paused = false; score = 0; wave = 0;
        cameraX = 0; cameraY = 0;
        people.clear(); police.clear(); cars.clear(); selected.clear();
        for (int i = 0; i < 14; i++) {
            float z = (i % 7 - 3) * (ROAD_HALF[stage] / 3.2f);
            people.add(makeUnit(0, 8 + i * 3, z));
        }
        for (int i = 0; i < 10; i++) cars.add(new Car(i * 22 - 40, i % 2 == 0 ? 1 : -1));
        if (side == 1) for (int i = 0; i < 6; i++) police.add(makeUnit(1, -15 + i * 3, -1.5f + i * .6f));
        lastTime = System.nanoTime();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        long now = System.nanoTime();
        float dt = lastTime == 0 ? 0 : Math.min(.05f, (now - lastTime) / 1_000_000_000f);
        lastTime = now;
        if (!menu && !paused) update(dt);
        drawWorld(c);
        if (menu) drawMenu(c);
        else drawHud(c);
        postInvalidateDelayed(16);
    }

    private void update(float dt) {
        wave = Math.min(4, (int)(score / 250));
        if (side == 0) {
            for (Unit u : people) moveToward(u, dt);
            if (score > 250 && police.size() < 5) police.add(makeUnit(1, 0, -2));
            if (score > 600 && police.size() < 9) police.add(makeUnit(1, 5, 2));
        } else {
            if (wave > 0 && people.size() < 18) {
                Unit u = makeUnit(0, 25 + random.nextInt(25), -3 + random.nextInt(7));
                u.targetX = 0; u.targetZ = 0; people.add(u);
            }
            for (Unit u : police) moveToward(u, dt);
            for (Unit u : people) moveToward(u, dt);
        }
        for (Car car : cars) {
            car.x += car.dir * car.speed * dt;
            if (car.x > MAP_LENGTH[stage] / 2f + 15) car.x = -MAP_LENGTH[stage] / 2f - 15;
            if (car.x < -MAP_LENGTH[stage] / 2f - 15) car.x = MAP_LENGTH[stage] / 2f + 15;
        }
        if (side == 0) {
            int stopped = 0;
            for (Car car : cars) if (nearBlocker(car)) stopped++;
            score += Math.round(stopped * 10 * dt);
        } else {
            score += Math.round(Math.max(0, 5 - people.size()) * dt);
            if (people.size() > 22) score = Math.max(0, score - Math.round(dt * 8));
        }
    }

    private boolean nearBlocker(Car car) {
        for (Unit u : people) if (Math.abs(u.x - car.x) < 3.0f && Math.abs(u.z) < ROAD_HALF[stage]) return true;
        return false;
    }

    private void moveToward(Unit u, float dt) {
        float dx = u.targetX - u.x, dz = u.targetZ - u.z;
        float d = (float)Math.sqrt(dx * dx + dz * dz);
        if (d > .1f) {
            u.x += dx / d * u.speed * dt;
            u.z += dz / d * u.speed * dt;
        }
    }

    private void drawWorld(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(26, 42, 52)); c.drawRect(0, 0, w, h, p);
        float roadTop = h * .31f, roadBottom = h * .74f;
        p.setColor(Color.rgb(55, 59, 63)); c.drawRect(0, roadTop, w, roadBottom, p);
        p.setColor(Color.rgb(185, 185, 175)); c.drawRect(0, roadTop - 10, w, roadTop, p);
        c.drawRect(0, roadBottom, w, roadBottom + 10, p);

        p.setStrokeWidth(4);
        p.setColor(Color.rgb(225, 196, 86));
        for (int x = 0; x < w; x += 42) c.drawLine(x, (roadTop+roadBottom)/2, x+22, (roadTop+roadBottom)/2, p);

        float sx = w / (float)MAP_LENGTH[stage];
        for (Car car : cars) {
            float x = w/2f + (car.x - cameraX) * sx;
            float z = (roadTop+roadBottom)/2f + car.dir * 55 + (car.z * 12);
            p.setColor(car.dir > 0 ? Color.rgb(52, 125, 181) : Color.rgb(190, 83, 67));
            c.drawRoundRect(x-18, z-9, x+18, z+9, 5, 5, p);
            p.setColor(Color.WHITE); c.drawRect(x-8, z-6, x+7, z+5, p);
        }
        for (Unit u : people) drawUnit(c, u, sx, roadTop, roadBottom);
        for (Unit u : police) drawPolice(c, u, sx, roadTop, roadBottom);
        if (selecting) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.YELLOW);
            c.drawRect(selectedRect, p); p.setStyle(Paint.Style.FILL);
        }
    }

    private float screenX(float worldX, float sx) { return getWidth()/2f + (worldX-cameraX)*sx; }
    private float screenY(float z, float top, float bottom) {
        return (top+bottom)/2f + z * 28;
    }

    private void drawUnit(Canvas c, Unit u, float sx, float top, float bottom) {
        float x = screenX(u.x, sx), y = screenY(u.z, top, bottom);
        p.setColor(Color.rgb(236, 197, 155)); c.drawCircle(x, y-12, 7, p);
        p.setColor(Color.rgb(48, 101, 70)); c.drawRect(x-8, y-5, x+8, y+13, p);
        p.setColor(Color.DKGRAY); c.drawRect(x-7, y+13, x-2, y+24, p); c.drawRect(x+2, y+13, x+7, y+24, p);
        if (selected.contains(u)) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.YELLOW); c.drawCircle(x,y,20,p); p.setStyle(Paint.Style.FILL); }
    }

    private void drawPolice(Canvas c, Unit u, float sx, float top, float bottom) {
        float x=screenX(u.x,sx), y=screenY(u.z,top,bottom);
        p.setColor(Color.rgb(220,190,155)); c.drawCircle(x,y-12,7,p);
        p.setColor(Color.rgb(45,65,105)); c.drawRect(x-8,y-5,x+8,y+14,p);
        p.setColor(Color.WHITE); c.drawRect(x-8,y-8,x+8,y-4,p);
        if (selected.contains(u)) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3); p.setColor(Color.CYAN); c.drawCircle(x,y,20,p); p.setStyle(Paint.Style.FILL); }
    }

    private void drawMenu(Canvas c) {
        int w=getWidth(), h=getHeight();
        p.setColor(0xDD101820); c.drawRect(0,0,w,h,p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        p.setTextSize(Math.max(28,w/22f)); p.setColor(Color.WHITE);
        c.drawText("חוסמים את הציר",w/2f,h*.22f,p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(18); p.setColor(0xFFB8C8D0);
        c.drawText("גרסה טבעית לאנדרואיד — Java, ללא JavaScript",w/2f,h*.28f,p);
        button(c,w*.14f,h*.43f,w*.38f,h*.58f,"כמפגינים");
        button(c,w*.42f,h*.43f,w*.66f,h*.58f,"כמפקד");
        button(c,w*.70f,h*.43f,w*.86f,h*.58f,"הגדרות");
        p.setTextSize(15); p.setColor(0xFFB8C8D0);
        c.drawText("עכבר מערכת: לחיצה לבחירה, גרירה לסימון/הזזה",w/2f,h*.70f,p);
    }

    private void button(Canvas c,float l,float t,float r,float b,String text) {
        p.setColor(0xFF2C5268); c.drawRoundRect(l,t,r,b,16,16,p);
        p.setColor(Color.WHITE); p.setTextSize(20); p.setTextAlign(Paint.Align.CENTER);
        c.drawText(text,(l+r)/2,(t+b)/2+7,p);
    }

    private void drawHud(Canvas c) {
        p.setColor(0xAA0B1116); c.drawRect(0,0,getWidth(),58,p);
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(17); p.setColor(Color.WHITE);
        c.drawText((side==0?"מפגינים":"מפקד")+"  •  מפה "+(stage+1)+"  •  גל "+wave,18,25,p);
        c.drawText("ניקוד: "+score,18,48,p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText(paused?"מושהה":"⏸",getWidth()-18,32,p);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x=e.getX(), y=e.getY();
        final int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            lastX=x; lastY=y; dragging=false; pointerDrag=false; draggedUnit=null;
            if (menu) {
                if (y>getHeight()*.40f && y<getHeight()*.62f) {
                    if (x>getWidth()*.68f) {
                        ((MainActivity)getContext()).showGameSettings();
                    } else {
                        side = x < getWidth()*.40f ? 0 : 1;
                        beginStage(0);
                    }
                    return true;
                }
                return true;
            }
            if (y<65) { paused=!paused; invalidate(); return true; }

            Unit hit = findUnitAt(x,y);
            boolean mouseLike = isMouseLike(e);
            if (hit != null && selected.contains(hit)) {
                draggedUnit = hit;
                pointerDrag = mouseLike;
                lastX=x; lastY=y;
                return true;
            }
            if (mouseLike && hit != null) {
                selected.clear();
                selected.add(hit);
                return true;
            }
            if (y>getHeight()*.30f && y<getHeight()*.76f) {
                selecting=true; selectedRect.set(x,y,x,y); return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (draggedUnit != null) {
                float dx=x-lastX, dy=y-lastY;
                if (Math.abs(dx)>0.5f || Math.abs(dy)>0.5f) {
                    float sx=getWidth()/(float)MAP_LENGTH[stage];
                    draggedUnit.targetX += dx/sx;
                    draggedUnit.targetZ += dy/28f;
                    draggedUnit.x = draggedUnit.targetX;
                    draggedUnit.z = draggedUnit.targetZ;
                    dragging=true;
                }
            } else if (selecting) {
                selectedRect.right=x; selectedRect.bottom=y;
            } else {
                float dx=x-lastX;
                if (Math.abs(dx)>2) { cameraX -= dx * 0.12f; dragging=true; }
            }
            lastX=x; lastY=y;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (draggedUnit != null) {
                draggedUnit = null;
            } else if (selecting) {
                selecting=false; selectUnits(selectedRect);
            } else if (!dragging) {
                commandAt(x,y);
            }
        }
        invalidate(); return true;
    }

    private boolean isMouseLike(MotionEvent e) {
        int source = e.getSource();
        return (source & InputDevice.SOURCE_CLASS_POINTER) != 0
                && (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
    }

    private Unit findUnitAt(float x,float y) {
        float sx=getWidth()/(float)MAP_LENGTH[stage];
        List<Unit> list=side==0?people:police;
        for (Unit u:list) {
            if (Math.abs(screenX(u.x,sx)-x)<24 &&
                    Math.abs(screenY(u.z,getHeight()*.31f,getHeight()*.74f)-y)<28) return u;
        }
        return null;
    }

    private void selectUnits(RectF r) {
        selected.clear();
        float sx=getWidth()/(float)MAP_LENGTH[stage];
        float l=Math.min(r.left,r.right), rr=Math.max(r.left,r.right);
        float t=Math.min(r.top,r.bottom), b=Math.max(r.top,r.bottom);
        for (Unit u : side==0?people:police) {
            float x=screenX(u.x,sx), y=screenY(u.z,getHeight()*.31f,getHeight()*.74f);
            if (new RectF(l,t,rr,b).contains(x,y)) selected.add(u);
        }
    }

    private void commandAt(float x,float y) {
        if (y>getHeight()-70) { menu=true; startMenuWorld(); return; }
        float sx=getWidth()/(float)MAP_LENGTH[stage];
        float wx=(x-getWidth()/2f)/sx+cameraX;
        float wz=(y-(getHeight()*.31f+getHeight()*.74f)/2f)/28f;
        if (!selected.isEmpty()) {
            int i=0;
            for (Unit u:selected) {
                u.targetX=wx+(i%3-1)*2.5f; u.targetZ=wz+(i/3)*1.8f; i++;
            }
        } else {
            Unit hit=findUnitAt(x,y);
            if (hit!=null) { selected.clear(); selected.add(hit); }
        }
    }

    private static final class Unit {
        int team; float x,z,targetX,targetZ,speed;
    }

    private static final class Car {
        float x,z,dir,speed=18;
        Car(float x,float dir){this.x=x;this.dir=dir;this.z=0;}
    }
}
