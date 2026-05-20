package org.example;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import java.nio.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class GameEngine {

    private long window;
    private final String title;
    private final int width = 1280, height = 720;
    private static final double FRAME_TIME = 1.0 / 60;
    private int fps, frameCount;
    private double lastFpsTime;

    private Shader       shader;
    private SteveShader  steveShader;
    private Camera       camera;
    private TextureAtlas atlas;
    private Steve        steve;
    private double       gameTime   = 0;
    private double       wheatTimer = 0;

    private int scoreGold = 0, scoreDiamond = 0, scoreWheat = 0;

    // ===== Collectible =====
    private static class Collectible {
        float x, y, z, bobOffset, rotY = 0;
        Block.Type type;
        boolean collected = false;
        Collectible(float x,float y,float z,Block.Type t,float bob){
            this.x=x;this.y=y;this.z=z;this.type=t;this.bobOffset=bob;
        }
    }
    private final List<Collectible> collectibles = new ArrayList<>();

    // ===== صوت بسيط بـ Java =====
    private void playCollectSound(boolean isDiamond) {
        new Thread(() -> {
            try {
                int freq = isDiamond ? 880 : 660;
                int dur  = isDiamond ? 300 : 200;
                byte[] buf = new byte[dur * 44100 / 1000];
                for (int i = 0; i < buf.length; i++) {
                    double angle = 2.0 * Math.PI * i * freq / 44100;
                    buf[i] = (byte)(Math.sin(angle) * 60 * Math.max(0, 1 - (double)i/buf.length));
                }
                javax.sound.sampled.AudioFormat fmt =
                        new javax.sound.sampled.AudioFormat(44100, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine line =
                        javax.sound.sampled.AudioSystem.getSourceDataLine(fmt);
                line.open(fmt); line.start();
                line.write(buf, 0, buf.length);
                line.drain(); line.close();
            } catch (Exception ignored) {}
        }).start();
    }

    private int[] lookedAt = null;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private static final int RENDER_DISTANCE = 4;
    private double lastMouseX, lastMouseY;
    private boolean firstMouse = true;

    private float playerFeetY = 40, playerVelY = 0;
    private boolean playerOnGround = false;

    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_WIDTH  = 0.3f;
    private static final float GRAVITY       = -25.0f;
    private static final float JUMP_FORCE    = 10.0f;

    private Block.Type selectedBlock = Block.Type.WHEAT_1;

    // ===== منشار 3D =====
    private int sawVao, sawVbo, sawEbo, sawCount;

    // ===== مجسم اللؤلؤ والماس =====
    private int pearlVao, pearlVbo, pearlEbo, pearlCount;
    private int diamondVao, diamondVbo, diamondEbo, diamondCount;

    public GameEngine(String title) { this.title = title; }
    public void run() { init(); gameLoop(); cleanup(); }

    private long chunkKey(int cx,int cz){return((long)cx<<32)|(cz&0xFFFFFFFFL);}

    private Chunk getOrCreateChunk(int cx,int cz){
        return chunks.computeIfAbsent(chunkKey(cx,cz),k->new Chunk(cx,cz));
    }

    private void updateChunks(){
        int pcx=(int)Math.floor(camera.position.x/Chunk.SIZE);
        int pcz=(int)Math.floor(camera.position.z/Chunk.SIZE);
        for(int dx=-RENDER_DISTANCE;dx<=RENDER_DISTANCE;dx++)
            for(int dz=-RENDER_DISTANCE;dz<=RENDER_DISTANCE;dz++)
                getOrCreateChunk(pcx+dx,pcz+dz);
        chunks.entrySet().removeIf(e->{
            Chunk c=e.getValue();
            int dx=Math.abs(c.getOffsetX()-pcx),dz=Math.abs(c.getOffsetZ()-pcz);
            if(dx>RENDER_DISTANCE+1||dz>RENDER_DISTANCE+1){c.cleanup();return true;}
            return false;
        });
    }

    private void spawnCollectibles(){
        Random rnd=new Random();
        for(int i=0;i<100;i++){
            float x=(float)(rnd.nextInt(160)-80);
            float z=(float)(rnd.nextInt(160)-80);
            Block.Type t=(rnd.nextInt(10)<3)?Block.Type.DIAMOND:Block.Type.GOLD;
            collectibles.add(new Collectible(x,playerFeetY+1.5f,z,t,rnd.nextFloat()*(float)Math.PI*2));
        }
    }

    // ===== بناء مجسم اللؤلؤ (كرة متعددة الأوجه) =====
    private void initPearlMesh() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int i = 0;

        float r = 0.35f;
        float[] colors = {0.95f, 0.90f, 0.95f}; // أبيض لؤلؤي

        // بنعمل octahedron (8 وجوه مثلثية) يشبه اللؤلؤ
        float[][] pts = {
                {0, r, 0},    // فوق
                {r, 0, 0},    // يمين
                {0, 0, r},    // أمام
                {-r, 0, 0},   // شمال
                {0, 0, -r},   // خلف
                {0, -r, 0},   // تحت
        };
        int[][] faces = {
                {0,1,2},{0,2,3},{0,3,4},{0,4,1},
                {5,2,1},{5,3,2},{5,4,3},{5,1,4},
        };

        for (int[] f : faces) {
            float bright = 0.7f + (float)Math.random() * 0.3f;
            for (int vi : f) {
                verts.add(pts[vi][0]);
                verts.add(pts[vi][1]);
                verts.add(pts[vi][2]);
                verts.add(0f); verts.add(0f);
                verts.add(bright);
            }
            idx.add(i); idx.add(i+1); idx.add(i+2);
            i += 3;
        }

        pearlCount = idx.size();
        pearlVao = glGenVertexArrays(); glBindVertexArray(pearlVao);
        pearlVbo = glGenBuffers();
        FloatBuffer fb = memAllocFloat(verts.size());
        for (float f2 : verts) fb.put(f2); fb.flip();
        glBindBuffer(GL_ARRAY_BUFFER, pearlVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW); memFree(fb);
        pearlEbo = glGenBuffers();
        IntBuffer ib = memAllocInt(idx.size());
        for (int id : idx) ib.put(id); ib.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, pearlEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW); memFree(ib);
        glVertexAttribPointer(0,3,GL_FLOAT,false,6*Float.BYTES,0); glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,2,GL_FLOAT,false,6*Float.BYTES,3*Float.BYTES); glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,1,GL_FLOAT,false,6*Float.BYTES,5*Float.BYTES); glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    // ===== بناء مجسم الماس (شكل ماسة حقيقية) =====
    private void initDiamondMesh() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int i = 0;

        float r = 0.3f, h1 = 0.4f, h2 = 0.25f;

        // الجزء العلوي من الماسة (مثل هرم مقطوع)
        float[][] top = {
                {0, h1, 0},       // قمة
                {r, 0, 0},        // يمين
                {0, 0, r},        // أمام
                {-r, 0, 0},       // شمال
                {0, 0, -r},       // خلف
                {r*0.7f, 0, r*0.7f},   // ركن أمام يمين
                {-r*0.7f, 0, r*0.7f},  // ركن أمام شمال
                {-r*0.7f, 0, -r*0.7f}, // ركن خلف شمال
                {r*0.7f, 0, -r*0.7f},  // ركن خلف يمين
        };

        // الجزء السفلي
        float[][] bot = {
                {0, -h2, 0},      // قاع
        };

        // وجوه الجزء العلوي
        int[][] topFaces = {
                {0,5,1},{0,1,8},{0,8,4},{0,4,7},{0,7,3},{0,3,6},{0,6,2},{0,2,5}
        };
        // وجوه الجزء السفلي
        int[][] botFaces = {
                {1,5,9},{5,2,9},{2,6,9},{6,3,9},{3,7,9},{7,4,9},{4,8,9},{8,1,9}
        };

        float[][] allPts = new float[top.length + bot.length][];
        System.arraycopy(top, 0, allPts, 0, top.length);
        allPts[top.length] = bot[0];

        float[][] brightFaces = new float[topFaces.length + botFaces.length][];
        for (int fi = 0; fi < topFaces.length; fi++) {
            float b = 0.6f + fi * 0.05f;
            brightFaces[fi] = new float[]{b};
        }
        for (int fi = 0; fi < botFaces.length; fi++) {
            float b = 0.4f + fi * 0.04f;
            brightFaces[topFaces.length + fi] = new float[]{b};
        }

        int[][] allFaces = new int[topFaces.length + botFaces.length][];
        System.arraycopy(topFaces, 0, allFaces, 0, topFaces.length);
        System.arraycopy(botFaces, 0, allFaces, topFaces.length, botFaces.length);

        for (int fi = 0; fi < allFaces.length; fi++) {
            int[] f = allFaces[fi];
            float bright = brightFaces[fi][0];
            for (int vi : f) {
                verts.add(allPts[vi][0]);
                verts.add(allPts[vi][1]);
                verts.add(allPts[vi][2]);
                verts.add(0f); verts.add(0f);
                verts.add(bright);
            }
            idx.add(i); idx.add(i+1); idx.add(i+2);
            i += 3;
        }

        diamondCount = idx.size();
        diamondVao = glGenVertexArrays(); glBindVertexArray(diamondVao);
        diamondVbo = glGenBuffers();
        FloatBuffer fb = memAllocFloat(verts.size());
        for (float f2 : verts) fb.put(f2); fb.flip();
        glBindBuffer(GL_ARRAY_BUFFER, diamondVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW); memFree(fb);
        diamondEbo = glGenBuffers();
        IntBuffer ib = memAllocInt(idx.size());
        for (int id : idx) ib.put(id); ib.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, diamondEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW); memFree(ib);
        glVertexAttribPointer(0,3,GL_FLOAT,false,6*Float.BYTES,0); glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,2,GL_FLOAT,false,6*Float.BYTES,3*Float.BYTES); glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,1,GL_FLOAT,false,6*Float.BYTES,5*Float.BYTES); glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    // ===== منشار 3D حقيقي =====
    private void initSaw() {
        List<Float> verts = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int i = 0;

        // جسم المنشار - لوح رفيع
        float w=0.05f, len=0.7f, h=0.15f;

        // الوجه الأمامي للجسم
        float[][] body = {
                {0,0,w}, {len,0,w}, {len,h,w}, {0,h,w},   // أمام
                {0,0,-w},{0,h,-w},{len,h,-w},{len,0,-w},   // خلف
                {0,h,w},{len,h,w},{len,h,-w},{0,h,-w},     // فوق
                {0,0,-w},{len,0,-w},{len,0,w},{0,0,w},     // تحت
        };
        float[] bodyBright = {0.85f, 0.75f, 0.95f, 0.65f};
        int[][] bodyFaces = {{0,1,2,3},{4,5,6,7},{8,9,10,11},{12,13,14,15}};

        for (int fi = 0; fi < bodyFaces.length; fi++) {
            int[] f = bodyFaces[fi];
            float bright = bodyBright[fi];
            // رمادي معدني
            float r2=0.7f*bright, g2=0.7f*bright, b2=0.75f*bright;
            for (int vi : f) {
                verts.add(body[vi][0]); verts.add(body[vi][1]); verts.add(body[vi][2]);
                verts.add(0f); verts.add(0f); verts.add(bright);
            }
            idx.add(i);idx.add(i+1);idx.add(i+2);
            idx.add(i+2);idx.add(i+3);idx.add(i);
            i+=4;
        }

        // أسنان المنشار
        int teeth = 8;
        float toothW = len / teeth;
        for (int t = 0; t < teeth; t++) {
            float tx = t * toothW;
            float[][] tooth = {
                    {tx+toothW*0.2f, h, w},
                    {tx+toothW*0.5f, h+0.08f, w},
                    {tx+toothW*0.8f, h, w},
            };
            for (float[] pt : tooth) {
                verts.add(pt[0]);verts.add(pt[1]);verts.add(pt[2]);
                verts.add(0f);verts.add(0f);verts.add(1.0f);
            }
            idx.add(i);idx.add(i+1);idx.add(i+2);
            i+=3;
            // نفس السن من الخلف
            float[][] tooth2 = {
                    {tx+toothW*0.2f, h, -w},
                    {tx+toothW*0.8f, h, -w},
                    {tx+toothW*0.5f, h+0.08f, -w},
            };
            for (float[] pt : tooth2) {
                verts.add(pt[0]);verts.add(pt[1]);verts.add(pt[2]);
                verts.add(0f);verts.add(0f);verts.add(1.0f);
            }
            idx.add(i);idx.add(i+1);idx.add(i+2);
            i+=3;
        }

        // المقبض
        float[][] handle = {
                {-0.2f,0.02f,w},{0,0.02f,w},{0,0.13f,w},{-0.2f,0.13f,w},
        };
        for (float[] pt : handle) {
            verts.add(pt[0]);verts.add(pt[1]);verts.add(pt[2]);
            verts.add(0f);verts.add(0f);verts.add(0.6f);
        }
        idx.add(i);idx.add(i+1);idx.add(i+2);
        idx.add(i+2);idx.add(i+3);idx.add(i);
        i+=4;

        sawCount = idx.size();
        sawVao = glGenVertexArrays(); glBindVertexArray(sawVao);
        sawVbo = glGenBuffers();
        FloatBuffer fb = memAllocFloat(verts.size());
        for (float f2 : verts) fb.put(f2); fb.flip();
        glBindBuffer(GL_ARRAY_BUFFER, sawVbo);
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW); memFree(fb);
        sawEbo = glGenBuffers();
        IntBuffer ib = memAllocInt(idx.size());
        for (int id : idx) ib.put(id); ib.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sawEbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW); memFree(ib);
        glVertexAttribPointer(0,3,GL_FLOAT,false,6*Float.BYTES,0); glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,2,GL_FLOAT,false,6*Float.BYTES,3*Float.BYTES); glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,1,GL_FLOAT,false,6*Float.BYTES,5*Float.BYTES); glEnableVertexAttribArray(2);
        glBindVertexArray(0);
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("فشل GLFW!");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE,   GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) throw new RuntimeException("فشل Window!");

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (firstMouse){lastMouseX=xpos;lastMouseY=ypos;firstMouse=false;}
            camera.rotate((float)(xpos-lastMouseX),(float)(ypos-lastMouseY));
            lastMouseX=xpos;lastMouseY=ypos;
        });

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key==GLFW_KEY_ESCAPE&&action==GLFW_RELEASE)
                glfwSetWindowShouldClose(win,true);
            if (key==GLFW_KEY_SPACE&&action==GLFW_PRESS&&playerOnGround){
                playerVelY=JUMP_FORCE;playerOnGround=false;
            }
            if (key==GLFW_KEY_1&&action==GLFW_PRESS) selectedBlock=Block.Type.WHEAT_1;
            if (key==GLFW_KEY_2&&action==GLFW_PRESS) selectedBlock=Block.Type.DIRT;
            if (key==GLFW_KEY_3&&action==GLFW_PRESS) selectedBlock=Block.Type.STONE;
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button==GLFW_MOUSE_BUTTON_LEFT&&action==GLFW_PRESS)  breakBlock();
            if (button==GLFW_MOUSE_BUTTON_RIGHT&&action==GLFW_PRESS) placeBlock();
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pw=stack.mallocInt(1),ph=stack.mallocInt(1);
            glfwGetWindowSize(window,pw,ph);
            GLFWVidMode vid=glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vid!=null) glfwSetWindowPos(window,
                    (vid.width()-pw.get(0))/2,(vid.height()-ph.get(0))/2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glClearColor(0.5f,0.8f,1.0f,1.0f);
        glEnable(GL_DEPTH_TEST);

        shader      = new Shader();
        steveShader = new SteveShader();
        atlas       = new TextureAtlas();

        playerFeetY = 40;
        camera = new Camera(0, playerFeetY+PLAYER_HEIGHT, 0);
        steve  = new Steve(5, 40, 5);

        initSaw();
        initPearlMesh();
        initDiamondMesh();
        updateChunks();
        spawnCollectibles();

        System.out.println("✓ OpenGL: " + glGetString(GL_VERSION));
        System.out.println("WASD حركة | Space قفز | يسار كسر/حصاد | يمين زرع/بناء");
        System.out.println("1=زرع | 2=تراب | 3=حجر | اقطع أشجار للكنز!");
    }

    private void gameLoop() {
        double prev=glfwGetTime(),lag=0;
        lastFpsTime=glfwGetTime();
        while (!glfwWindowShouldClose(window)) {
            double now=glfwGetTime(),elapsed=now-prev;
            prev=now;lag+=elapsed;
            glfwPollEvents();
            while (lag>=FRAME_TIME){update((float)FRAME_TIME);lag-=FRAME_TIME;}
            render();
            countFPS(now);
        }
    }

    private void update(float dt) {
        if (glfwGetKey(window,GLFW_KEY_W)==GLFW_PRESS) camera.moveForward(dt);
        if (glfwGetKey(window,GLFW_KEY_S)==GLFW_PRESS) camera.moveBackward(dt);
        if (glfwGetKey(window,GLFW_KEY_A)==GLFW_PRESS) camera.moveLeft(dt);
        if (glfwGetKey(window,GLFW_KEY_D)==GLFW_PRESS) camera.moveRight(dt);

        playerVelY+=GRAVITY*dt;
        playerFeetY+=playerVelY*dt;
        resolveVertical();
        resolveHorizontal();
        camera.position.y=playerFeetY+PLAYER_HEIGHT;

        Vector3f forward=camera.getForward().normalize();
        steve.x=camera.position.x+forward.x*3;
        steve.z=camera.position.z+forward.z*3;
        steve.y=playerFeetY;
        steve.velocityY=0;steve.onGround=true;

        wheatTimer+=dt;
        if (wheatTimer>=5.0){wheatTimer=0;growWheat();}

        updateCollectibles(dt);
        lookedAt=raycast(false);
        updateChunks();
        gameTime+=dt;
    }

    private void updateCollectibles(float dt) {
        collectibles.removeIf(col -> {
            if (col.collected) return true;
            col.y=playerFeetY+1.5f+(float)Math.sin(gameTime*2+col.bobOffset)*0.3f;
            col.rotY+=(float)(dt*2.5f);
            float dx=col.x-camera.position.x;
            float dz=col.z-camera.position.z;
            if ((float)Math.sqrt(dx*dx+dz*dz)<1.5f) {
                boolean isDiamond = col.type==Block.Type.DIAMOND;
                if (isDiamond) { scoreDiamond+=20; System.out.println("💎 ماس! +20"); }
                else           { scoreGold+=5;     System.out.println("⭐ لؤلؤ! +5"); }
                playCollectSound(isDiamond);
                col.collected=true;
                return true;
            }
            return false;
        });
        if (collectibles.isEmpty()) spawnCollectibles();
    }

    private void growWheat() {
        for (Chunk chunk:chunks.values())
            for (int x=0;x<Chunk.SIZE;x++)
                for (int y=0;y<Chunk.HEIGHT;y++)
                    for (int z=0;z<Chunk.SIZE;z++){
                        Block.Type b=chunk.getBlock(x,y,z);
                        Block.Type next=switch(b){
                            case WHEAT_1->Block.Type.WHEAT_2;
                            case WHEAT_2->Block.Type.WHEAT_3;
                            case WHEAT_3->Block.Type.WHEAT_READY;
                            default->null;
                        };
                        if(next!=null)chunk.setBlock(x,y,z,next);
                    }
    }

    private void resolveVertical() {
        float px=camera.position.x,pz=camera.position.z;
        if (playerVelY<=0){
            int by=(int)Math.floor(playerFeetY-0.05f);
            boolean hit=false;
            for (float ox:new float[]{-PLAYER_WIDTH,0,PLAYER_WIDTH}){
                for (float oz:new float[]{-PLAYER_WIDTH,0,PLAYER_WIDTH}){
                    if (isSolid(getBlockWorld((int)Math.floor(px+ox),by,(int)Math.floor(pz+oz)))){
                        playerFeetY=by+1;playerVelY=0;playerOnGround=true;hit=true;break;
                    }
                }
                if(hit)break;
            }
            if(!hit)playerOnGround=false;
        } else {
            int headY=(int)Math.floor(playerFeetY+PLAYER_HEIGHT+0.05f);
            for (float ox:new float[]{-PLAYER_WIDTH,0,PLAYER_WIDTH})
                for (float oz:new float[]{-PLAYER_WIDTH,0,PLAYER_WIDTH})
                    if(isSolid(getBlockWorld((int)Math.floor(px+ox),headY,(int)Math.floor(pz+oz)))){
                        playerFeetY=headY-PLAYER_HEIGHT;playerVelY=0;
                    }
        }
        if(playerFeetY<0){playerFeetY=40;playerVelY=0;playerOnGround=false;}
    }

    private void resolveHorizontal() {
        float px=camera.position.x,pz=camera.position.z;
        for (float oy:new float[]{0.1f,0.9f,1.7f}){
            int by=(int)Math.floor(playerFeetY+oy);
            if(isSolid(getBlockWorld((int)Math.floor(px+PLAYER_WIDTH),by,(int)Math.floor(pz))))
                camera.position.x=(float)Math.floor(px+PLAYER_WIDTH)-PLAYER_WIDTH;
            if(isSolid(getBlockWorld((int)Math.floor(px-PLAYER_WIDTH),by,(int)Math.floor(pz))))
                camera.position.x=(float)Math.floor(px-PLAYER_WIDTH)+1+PLAYER_WIDTH;
            if(isSolid(getBlockWorld((int)Math.floor(px),by,(int)Math.floor(pz+PLAYER_WIDTH))))
                camera.position.z=(float)Math.floor(pz+PLAYER_WIDTH)-PLAYER_WIDTH;
            if(isSolid(getBlockWorld((int)Math.floor(px),by,(int)Math.floor(pz-PLAYER_WIDTH))))
                camera.position.z=(float)Math.floor(pz-PLAYER_WIDTH)+1+PLAYER_WIDTH;
        }
    }

    private boolean isSolid(Block.Type b){
        return b!=null&&b!=Block.Type.AIR&&b!=Block.Type.WATER
                &&b!=Block.Type.WHEAT_1&&b!=Block.Type.WHEAT_2
                &&b!=Block.Type.WHEAT_3&&b!=Block.Type.WHEAT_READY
                &&b!=Block.Type.GOLD&&b!=Block.Type.DIAMOND;
    }

    // ===== قطع الشجرة = كنز محتمل =====
    private void breakBlock() {
        int[] hit=raycast(false);
        if(hit==null)return;
        Block.Type b=getBlockWorld(hit[0],hit[1],hit[2]);

        if(b==Block.Type.WHEAT_READY){
            scoreWheat+=10;
            System.out.println("🌾 حصدت! +10");
        }

        // لو قطعت خشب — ممكن تلاقي كنز تحته
        if(b==Block.Type.WOOD){
            Random rnd=new Random();
            if(rnd.nextInt(4)==0){ // ٢٥٪ احتمال كنز
                Block.Type treasure=(rnd.nextInt(3)==0)?Block.Type.DIAMOND:Block.Type.GOLD;
                // الكنز بيظهر تحت الشجرة
                collectibles.add(new Collectible(
                        hit[0]+0.5f, hit[1]-1f, hit[2]+0.5f,
                        treasure, rnd.nextFloat()*(float)Math.PI*2
                ));
                System.out.println(treasure==Block.Type.DIAMOND ?
                        "💎 وجدت ماس تحت الشجرة!" : "⭐ وجدت لؤلؤ تحت الشجرة!");
            }
        }

        setBlockWorld(hit[0],hit[1],hit[2],Block.Type.AIR);
    }

    private void placeBlock() {
        int[] hit=raycast(false);
        int[] prev=raycast(true);
        if(hit==null||prev==null)return;
        int px=(int)Math.floor(camera.position.x);
        int py=(int)Math.floor(playerFeetY);
        int pz=(int)Math.floor(camera.position.z);
        if(prev[0]==px&&prev[2]==pz&&(prev[1]==py||prev[1]==py+1))return;
        if(selectedBlock==Block.Type.WHEAT_1){
            Block.Type below=getBlockWorld(prev[0],prev[1]-1,prev[2]);
            if(below!=Block.Type.DIRT&&below!=Block.Type.GRASS)return;
        }
        setBlockWorld(prev[0],prev[1],prev[2],selectedBlock);
    }

    private int[] raycast(boolean returnPrev){
        Vector3f dir=camera.getForward().normalize();
        int[] prev=null;
        for(float t=0;t<6f;t+=0.05f){
            int bx=(int)Math.floor(camera.position.x+dir.x*t);
            int by=(int)Math.floor(camera.position.y+dir.y*t);
            int bz=(int)Math.floor(camera.position.z+dir.z*t);
            int[] cur={bx,by,bz};
            Block.Type b=getBlockWorld(bx,by,bz);
            if(b!=null&&b!=Block.Type.AIR)
                return returnPrev?(prev!=null?prev:cur):cur;
            prev=cur;
        }
        return null;
    }

    private Block.Type getBlockWorld(int wx,int wy,int wz){
        int cx=Math.floorDiv(wx,Chunk.SIZE),cz=Math.floorDiv(wz,Chunk.SIZE);
        Chunk c=chunks.get(chunkKey(cx,cz));
        if(c==null)return null;
        return c.getBlock(Math.floorMod(wx,Chunk.SIZE),wy,Math.floorMod(wz,Chunk.SIZE));
    }

    private void setBlockWorld(int wx,int wy,int wz,Block.Type type){
        int cx=Math.floorDiv(wx,Chunk.SIZE),cz=Math.floorDiv(wz,Chunk.SIZE);
        Chunk c=chunks.get(chunkKey(cx,cz));
        if(c==null)return;
        c.setBlock(Math.floorMod(wx,Chunk.SIZE),wy,Math.floorMod(wz,Chunk.SIZE),type);
    }

    private void render(){
        glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        Matrix4f view=camera.getViewMatrix();
        Matrix4f proj=new Matrix4f().perspective(
                (float)Math.toRadians(70f),(float)width/height,0.1f,500f);

        // العالم
        shader.bind();atlas.bind();
        shader.setInt("uTexture",0);
        shader.setMatrix4f("view",view);
        shader.setMatrix4f("projection",proj);
        shader.setMatrix4f("model",new Matrix4f().identity());
        for(Chunk chunk:chunks.values())chunk.render();
        atlas.unbind();shader.unbind();

        if(lookedAt!=null)
            drawBlockHighlight(view,proj,lookedAt[0],lookedAt[1],lookedAt[2]);

        // رسم الـ collectibles بمجسماتها
        drawCollectibles(view,proj);

        // Steve + منشار
        steveShader.bind();
        steveShader.setMatrix4f("view",view);
        steveShader.setMatrix4f("projection",proj);
        steveShader.setInt("uTex",0);
        steveShader.setFloat("uBright",1.0f);
        boolean moving=glfwGetKey(window,GLFW_KEY_W)==GLFW_PRESS||
                glfwGetKey(window,GLFW_KEY_S)==GLFW_PRESS||
                glfwGetKey(window,GLFW_KEY_A)==GLFW_PRESS||
                glfwGetKey(window,GLFW_KEY_D)==GLFW_PRESS;
        steve.render(steveShader,camera.getYaw()+180,gameTime,moving);
        drawSaw(view,proj,moving);
        steveShader.unbind();

        drawCrosshair();
        glfwSwapBuffers(window);
    }

    // ===== رسم المجسمات =====
    private void drawCollectibles(Matrix4f view,Matrix4f proj){
        steveShader.bind();
        steveShader.setMatrix4f("view",view);
        steveShader.setMatrix4f("projection",proj);

        for(Collectible col:collectibles){
            if(col.collected)continue;
            Matrix4f model=new Matrix4f()
                    .translate(col.x,col.y,col.z)
                    .rotateY(col.rotY);

            steveShader.setMatrix4f("model",model);

            if(col.type==Block.Type.DIAMOND){
                // ماسة زرقا لامعة
                steveShader.setFloat("uBright",
                        0.7f+(float)Math.sin(gameTime*3+col.bobOffset)*0.3f);
                glBindVertexArray(diamondVao);
                glDrawElements(GL_TRIANGLES,diamondCount,GL_UNSIGNED_INT,0);
            } else {
                // لؤلؤة بيضا لامعة
                steveShader.setFloat("uBright",
                        0.8f+(float)Math.sin(gameTime*2+col.bobOffset)*0.2f);
                glBindVertexArray(pearlVao);
                glDrawElements(GL_TRIANGLES,pearlCount,GL_UNSIGNED_INT,0);
            }
            glBindVertexArray(0);
        }
        steveShader.unbind();
    }

    private void drawSaw(Matrix4f view,Matrix4f proj,boolean moving){
        Vector3f forward=camera.getForward().normalize();
        float sx=camera.position.x+forward.x*3;
        float sz=camera.position.z+forward.z*3;
        float rotY=(float)Math.toRadians(camera.getYaw()+180);
        float swing=moving?(float)Math.sin(gameTime*8)*0.3f:0;

        Matrix4f sawModel=new Matrix4f()
                .translate(sx,steve.y,sz)
                .rotateY(rotY)
                .translate(0.35f,1.15f,0.2f)
                .rotateX(swing-0.3f)
                .rotateZ((float)Math.toRadians(-20))
                .scale(0.9f);

        steveShader.setMatrix4f("model",sawModel);
        steveShader.setFloat("uBright",0.9f);
        glBindVertexArray(sawVao);
        glDrawElements(GL_TRIANGLES,sawCount,GL_UNSIGNED_INT,0);
        glBindVertexArray(0);
    }

    private void drawBlockHighlight(Matrix4f view,Matrix4f proj,int bx,int by,int bz){
        glPolygonOffset(-1f,-1f);
        glEnable(GL_POLYGON_OFFSET_LINE);
        glPolygonMode(GL_FRONT_AND_BACK,GL_LINE);
        glLineWidth(2.0f);
        steveShader.bind();
        steveShader.setMatrix4f("view",view);
        steveShader.setMatrix4f("projection",proj);
        steveShader.setMatrix4f("model",new Matrix4f()
                .translate(bx+0.5f,by+0.5f,bz+0.5f).scale(1.002f));
        steveShader.setFloat("uBright",0.0f);
        steveShader.setInt("uTex",0);
        int vao=glGenVertexArrays(),vbo=glGenBuffers(),ebo=glGenBuffers();
        glBindVertexArray(vao);
        float[] v={
                -0.5f,-0.5f,-0.5f,0,0,1, 0.5f,-0.5f,-0.5f,0,0,1,
                0.5f, 0.5f,-0.5f,0,0,1,-0.5f, 0.5f,-0.5f,0,0,1,
                -0.5f,-0.5f, 0.5f,0,0,1, 0.5f,-0.5f, 0.5f,0,0,1,
                0.5f, 0.5f, 0.5f,0,0,1,-0.5f, 0.5f, 0.5f,0,0,1,
        };
        int[] idx={0,1,2,2,3,0,4,5,6,6,7,4,0,4,7,7,3,0,
                1,5,6,6,2,1,3,2,6,6,7,3,0,1,5,5,4,0};
        FloatBuffer fb=memAllocFloat(v.length);fb.put(v).flip();
        glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,fb,GL_DYNAMIC_DRAW);memFree(fb);
        IntBuffer ib=memAllocInt(idx.length);ib.put(idx).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);glBufferData(GL_ELEMENT_ARRAY_BUFFER,ib,GL_DYNAMIC_DRAW);memFree(ib);
        glVertexAttribPointer(0,3,GL_FLOAT,false,6*Float.BYTES,0);glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,2,GL_FLOAT,false,6*Float.BYTES,3*Float.BYTES);glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,1,GL_FLOAT,false,6*Float.BYTES,5*Float.BYTES);glEnableVertexAttribArray(2);
        glDrawElements(GL_TRIANGLES,idx.length,GL_UNSIGNED_INT,0);
        glDeleteBuffers(vbo);glDeleteBuffers(ebo);glDeleteVertexArrays(vao);
        steveShader.unbind();
        glPolygonMode(GL_FRONT_AND_BACK,GL_FILL);
        glDisable(GL_POLYGON_OFFSET_LINE);
    }

    private void drawCrosshair(){
        glDisable(GL_DEPTH_TEST);
        steveShader.bind();
        steveShader.setMatrix4f("projection",new Matrix4f().ortho(0,width,height,0,-1,1));
        steveShader.setMatrix4f("view",new Matrix4f().identity());
        steveShader.setMatrix4f("model",new Matrix4f().identity());
        steveShader.setFloat("uBright",1.0f);
        float cx=width/2f,cy=height/2f,s=4f;
        float[] dot={cx-s,cy-s,0,0,0,1, cx+s,cy-s,0,0,0,1,
                cx+s,cy+s,0,0,0,1, cx-s,cy+s,0,0,0,1};
        int[] idx={0,1,2,2,3,0};
        int vao=glGenVertexArrays(),vbo=glGenBuffers(),ebo=glGenBuffers();
        glBindVertexArray(vao);
        FloatBuffer fb=memAllocFloat(dot.length);fb.put(dot).flip();
        glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,fb,GL_DYNAMIC_DRAW);memFree(fb);
        IntBuffer ib=memAllocInt(idx.length);ib.put(idx).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);glBufferData(GL_ELEMENT_ARRAY_BUFFER,ib,GL_DYNAMIC_DRAW);memFree(ib);
        glVertexAttribPointer(0,3,GL_FLOAT,false,6*Float.BYTES,0);glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,2,GL_FLOAT,false,6*Float.BYTES,3*Float.BYTES);glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,1,GL_FLOAT,false,6*Float.BYTES,5*Float.BYTES);glEnableVertexAttribArray(2);
        glDrawElements(GL_TRIANGLES,idx.length,GL_UNSIGNED_INT,0);
        glDeleteBuffers(vbo);glDeleteBuffers(ebo);glDeleteVertexArrays(vao);
        steveShader.unbind();
        glEnable(GL_DEPTH_TEST);
    }

    private void countFPS(double now){
        frameCount++;
        if(now-lastFpsTime>=1.0){
            fps=frameCount;frameCount=0;lastFpsTime=now;
            int total=scoreGold+scoreDiamond+scoreWheat;
            glfwSetWindowTitle(window,
                    title+" | FPS:"+fps+
                            " | ⭐لؤلؤ:"+scoreGold+
                            " | 💎ماس:"+scoreDiamond+
                            " | 🌾قمح:"+scoreWheat+
                            " | المجموع:"+total);
        }
    }

    private void cleanup(){
        shader.cleanup();steveShader.cleanup();atlas.cleanup();steve.cleanup();
        glDeleteBuffers(sawVbo);glDeleteBuffers(sawEbo);glDeleteVertexArrays(sawVao);
        glDeleteBuffers(pearlVbo);glDeleteBuffers(pearlEbo);glDeleteVertexArrays(pearlVao);
        glDeleteBuffers(diamondVbo);glDeleteBuffers(diamondEbo);glDeleteVertexArrays(diamondVao);
        for(Chunk c:chunks.values())c.cleanup();
        glfwFreeCallbacks(window);glfwDestroyWindow(window);
        glfwTerminate();glfwSetErrorCallback(null).free();
    }
}