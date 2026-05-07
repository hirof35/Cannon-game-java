package cannonGame;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

public class CannonGame extends JPanel implements ActionListener {
    enum State { TITLE, PLAYING, GAMEOVER, CLEAR }
    private State gameState = State.TITLE;

    private final int WIDTH = 800, HEIGHT = 600;
    private int[] terrainY = new int[WIDTH];
    private double ballX, ballY, vx, vy;
    
    // --- 調整変数 ---
    private double currentPower = 0;
    private final double MAX_POWER = 35.0; // 出力を少し強化
    private final double GRAVITY = 0.8;    // 重力を少し重くして弾道を安定化
    private double chargeSpeed = 0.8;      // パワーが溜まる速度
    private int cannonAngle = 45;
    private int ammo = 5;
    // ----------------

    private int targetX, targetY;
    private boolean flying = false, isCharging = false;
    private ArrayList<Explosion> explosions = new ArrayList<>();
    private Clip shootSound, explosionSound, clearSound;

    public CannonGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(135, 206, 235));
        loadSounds();
        generateTerrain();
        resetTarget();

        Timer timer = new Timer(20, this);
        timer.start();

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                if (gameState != State.PLAYING) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE) resetGame();
                    return;
                }
                // 角度調整のステップを細かくし、操作性を向上
                if (e.getKeyCode() == KeyEvent.VK_UP) cannonAngle = Math.min(90, cannonAngle + 2);
                if (e.getKeyCode() == KeyEvent.VK_DOWN) cannonAngle = Math.max(0, cannonAngle - 2);
                
                // スペースキー長押しでチャージ開始
                if (e.getKeyCode() == KeyEvent.VK_SPACE && !flying && ammo > 0) {
                    isCharging = true;
                }
            }
            public void keyReleased(KeyEvent e) {
                if (gameState == State.PLAYING && e.getKeyCode() == KeyEvent.VK_SPACE && isCharging) {
                    launch();
                    isCharging = false;
                }
            }
        });
    }

    private void generateTerrain() {
        for (int x = 0; x < WIDTH; x++) {
            terrainY[x] = 500 + (int)(Math.sin(x * 0.015) * 40 + Math.sin(x * 0.04) * 15);
        }
    }

    private void resetTarget() {
        targetX = 450 + (int)(Math.random() * 300);
        targetY = terrainY[targetX] - 20;
    }

    private void resetGame() {
        ammo = 5;
        currentPower = 0;
        flying = false;
        isCharging = false;
        explosions.clear();
        generateTerrain();
        resetTarget();
        gameState = State.PLAYING;
    }

    private void launch() {
        ballX = 40; 
        ballY = terrainY[40] - 15;
        double rad = Math.toRadians(cannonAngle);
        
        // パワー出力を適用
        vx = Math.cos(rad) * currentPower;
        vy = -Math.sin(rad) * currentPower;
        
        flying = true;
        ammo--;
        playSound(shootSound);
        // 発射後、次のためにパワーをリセットしない（actionPerformedで離した瞬間にリセットされるよう修正）
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == State.PLAYING) {
            // パワーチャージのロジック（最大まで行くと0に戻る循環式、または最大で止まる）
            if (isCharging) {
                currentPower += chargeSpeed;
                if (currentPower > MAX_POWER) {
                    currentPower = 0; // ループさせてタイミングを計るゲーム性を追加
                }
            }

            if (flying) {
                ballX += vx; 
                ballY += vy; 
                vy += GRAVITY;

                // 命中判定
                if (Math.hypot(ballX - targetX, ballY - targetY) < 25) {
                    explosions.add(new Explosion(targetX, targetY, Color.YELLOW));
                    playSound(clearSound);
                    gameState = State.CLEAR;
                    flying = false;
                    currentPower = 0; // パワーリセット
                }
                // 地面・画面外判定
                else if (ballX < 0 || ballX >= WIDTH || ballY >= terrainY[(int)Math.max(0, Math.min(WIDTH-1, ballX))]) {
                    if (ballX >= 0 && ballX < WIDTH) {
                        explosions.add(new Explosion(ballX, terrainY[(int)ballX], Color.ORANGE));
                        playSound(explosionSound);
                    }
                    flying = false;
                    currentPower = 0; // 着弾時にパワーリセット
                    if (ammo <= 0) gameState = State.GAMEOVER;
                }
            }
        }

        Iterator<Explosion> it = explosions.iterator();
        while (it.hasNext()) {
            Explosion ex = it.next();
            ex.update();
            if (!ex.active) it.remove();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景と地形
        g.setColor(new Color(50, 100, 50));
        Polygon p = new Polygon();
        for (int x = 0; x < WIDTH; x++) p.addPoint(x, terrainY[x]);
        p.addPoint(WIDTH, HEIGHT); p.addPoint(0, HEIGHT);
        g.fillPolygon(p);

        // 的
        g.setColor(Color.RED);
        g.fillOval(targetX - 15, targetY - 15, 30, 30);
        g.setColor(Color.WHITE);
        g.fillOval(targetX - 7, targetY - 7, 14, 14);

        // 大砲
        int cx = 40, cy = terrainY[40];
        g2.translate(cx, cy);
        g2.rotate(-Math.toRadians(cannonAngle));
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, -8, 45, 16);
        g2.rotate(Math.toRadians(cannonAngle));
        g2.translate(-cx, -cy);

        // 弾
        if (flying) {
            g.setColor(Color.BLACK);
            g.fillOval((int)ballX - 6, (int)ballY - 6, 12, 12);
        }

        for (Explosion ex : explosions) ex.draw(g);
        drawUI(g);
    }

    private void drawUI(Graphics g) {
        if (gameState == State.TITLE) {
            drawOverlay(g, "CANNON BLASTER", "Press SPACE to Start");
        } else if (gameState == State.PLAYING) {
            g.setColor(Color.BLACK);
            g.setFont(new Font("Monospaced", Font.BOLD, 18));
            g.drawString(String.format("AMMO: %d", ammo), 20, 30);
            g.drawString(String.format("ANGLE: %d°", cannonAngle), 20, 55);
            
            // パワーバーの装飾
            g.setColor(Color.GRAY);
            g.drawRect(20, 70, 150, 15);
            if (isCharging) {
                // チャージ中は色を変えるなどの視覚効果
                g.setColor(currentPower > MAX_POWER * 0.8 ? Color.ORANGE : Color.RED);
            } else {
                g.setColor(Color.DARK_GRAY);
            }
            g.fillRect(21, 71, (int)(currentPower / MAX_POWER * 149), 14);
        } else if (gameState == State.GAMEOVER) {
            drawOverlay(g, "OUT OF AMMO", "Press SPACE to Retry");
        } else if (gameState == State.CLEAR) {
            drawOverlay(g, "TARGET DESTROYED!", "Press SPACE to Next Stage");
        }
    }

    private void drawOverlay(Graphics g, String main, String sub) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Impact", Font.PLAIN, 60));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(main, (WIDTH - fm.stringWidth(main)) / 2, HEIGHT / 2);
        g.setFont(new Font("Arial", Font.PLAIN, 20));
        g.drawString(sub, (WIDTH - g.getFontMetrics().stringWidth(sub)) / 2, HEIGHT / 2 + 60);
    }

    // --- Sound Helper (Error handling) ---
    private void loadSounds() {
        shootSound = loadClip("shoot.wav");
        explosionSound = loadClip("explosion.wav");
        clearSound = loadClip("clear.wav");
    }

    private Clip loadClip(String fileName) {
        try {
            File file = new File(fileName);
            if (!file.exists()) return null;
            AudioInputStream ais = AudioSystem.getAudioInputStream(file);
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (Exception e) { return null; }
    }

    private void playSound(Clip clip) {
        if (clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    public static void main(String[] args) {
        JFrame f = new JFrame("Java Cannon Game");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.add(new CannonGame());
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    class Explosion {
        double x, y; int size = 0, alpha = 255; 
        boolean active = true; Color color;
        Explosion(double x, double y, Color c) { this.x = x; this.y = y; this.color = c; }
        void update() { size += 6; alpha -= 12; if (alpha <= 0) active = false; }
        void draw(Graphics g) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, alpha)));
            g.fillOval((int)x - size/2, (int)y - size/2, size, size);
        }
    }
}
