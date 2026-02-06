import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;

public class Reversi extends JPanel {
    public final static int UNIT_SIZE = 80;
    Board board = new Board();
    private Player[] player = new Player[2];
    private int turn; // Stone.black or Stone.white

    public Reversi(int strategy) {
        setPreferredSize(new Dimension(UNIT_SIZE * 10, UNIT_SIZE * 10));
        addMouseListener(new MouseProc());
        player[0] = new Player(Stone.black, Player.type_human, 0);
        player[1] = new Player(Stone.white, Player.type_computer, strategy);
        turn = Stone.black;

        // 最初に合法手を計算しておく
        board.evaluateBoard();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        board.paint(g, UNIT_SIZE);

        g.setColor(Color.white);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));

        // 手番メッセージ（左上）
        String msg1 = (turn == Stone.black) ? "黒の番です" : "白の番です";
        if (player[turn - 1].getType() == Player.type_computer) {
            msg1 += "（考えています）";
        }
        g.drawString(msg1, UNIT_SIZE / 2, UNIT_SIZE / 2);

        // 石数表示（左下） [黒:8, 白:4] 形式
        int blackCount = board.countStone(Stone.black);
        int whiteCount = board.countStone(Stone.white);
        String countMsg = "[黒:" + blackCount + ", 白:" + whiteCount + "]";
        g.drawString(countMsg, 20, getHeight() - 20);
    }

    void changeTurn() {
        if (turn == Stone.black) {
            turn = Stone.white;
        } else if (turn == Stone.white) {
            turn = Stone.black;
        }
    }

    void MessageDialog(String str) {
        JOptionPane.showMessageDialog(this, str, "情報", JOptionPane.INFORMATION_MESSAGE);
    }

    void EndMessageDialog() {
        int black = board.countStone(Stone.black);
        int white = board.countStone(Stone.white);
        String str = "[黒:" + black + ", 白:" + white + "]で";
        if (black > white) {
            str += "黒の勝ち";
        } else if (black < white) {
            str += "白の勝ち";
        } else {
            str += "引き分け";
        }
        JOptionPane.showMessageDialog(this, str, "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
        System.exit(0);
    }

    public static void main(String[] args) {

        int strategy = 1;
        if (args.length >= 1) {
            try {
                int v = Integer.parseInt(args[0]);
                if (v >= 1 && v <= 3) {
                    strategy = v;
                } else {
                    System.out.println("戦略は 1〜3 のいずれかです。1(ランダム)を使います。");
                }
            } catch (NumberFormatException e) {
                System.out.println("引数は 1,2,3 のいずれかの整数にしてください。1(ランダム)を使います。");
            }
            } else {
            System.out.println("戦略引数が指定されていません。1(ランダム)を使います。");
        }

        JFrame f = new JFrame("Reversi");
        f.getContentPane().setLayout(new FlowLayout());
        Reversi game = new Reversi(strategy);
        f.getContentPane().add(game);
        f.pack();
        f.setResizable(false);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    // ==== マウス処理（人間の手） ====
    class MouseProc extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            // コンピュータの番のときはクリック無視
            if (player[turn - 1].getType() == Player.type_computer) {
                return;
            }

            Point point = e.getPoint();
            Point gp = new Point();
            gp.x = point.x / UNIT_SIZE - 1;
            gp.y = point.y / UNIT_SIZE - 1;

            // 盤面外は無視
            if (gp.x < 0 || gp.x >= 8 || gp.y < 0 || gp.y >= 8) {
                return;
            }

            // 現在の盤面で合法手を再計算
            board.evaluateBoard();

            int color = player[turn - 1].getColor();

            // 現在のプレイヤーに合法手が一つもない → パス
            boolean hasMove = (color == Stone.black)
                    ? (board.num_grid_black > 0)
                    : (board.num_grid_white > 0);

            if (!hasMove) {
                MessageDialog("あなたはパスです");
                changeTurn();
                repaint();

                // 次がコンピュータなら思考スレッド開始
                if (player[turn - 1].getType() == Player.type_computer) {
                    Thread th = new TacticsThread();
                    th.start();
                }
                return;
            }

            // クリックしたマスが合法手か？
            int canReverse = 0;
            if (color == Stone.black) {
                canReverse = board.eval_black[gp.x][gp.y];
            } else {
                canReverse = board.eval_white[gp.x][gp.y];
            }

            // 裏返せる石が0 → 不正な場所
            if (canReverse <= 0) {
                repaint();
                return;
            }

            // 合法手なので置く & 反転
            board.placeAndFlip(gp.x, gp.y, color);

            // 石数を数える
            int blackCount = board.countStone(Stone.black);
            int whiteCount = board.countStone(Stone.white);

            // 新しい盤面で合法手を再計算
            board.evaluateBoard();

            // ゲーム終了判定（盤面が埋まる or 両方打てない）
            boolean isBoardFull = (blackCount + whiteCount == 64);
            boolean noMovesBoth = (board.num_grid_black == 0 && board.num_grid_white == 0);

            if (isBoardFull || noMovesBoth) {
                repaint();
                SwingUtilities.invokeLater(() -> EndMessageDialog());
                return;
            }

            // 手番交代
            changeTurn();
            repaint();

            // 次がコンピュータなら思考スレッド開始
            if (player[turn - 1].getType() == Player.type_computer) {
                Thread th = new TacticsThread();
                th.start();
            }
        }
    }

    // ==== コンピュータの手を打つスレッド ====
    class TacticsThread extends Thread {
        @Override
        public void run() {
            try {
                // 考えている演出
                Thread.sleep(2000);

                // 現在の盤面で合法手を再計算
                board.evaluateBoard();

                int color = player[turn - 1].getColor();
                boolean hasMove = (color == Stone.black)
                        ? (board.num_grid_black > 0)
                        : (board.num_grid_white > 0);

                if (!hasMove) {
                    // コンピュータもパス
                    MessageDialog("相手はパスです");
                    changeTurn();
                    repaint();
                    return;
                }

                // とりあえず最初に見つかった合法手を打つ単純戦略
                Point nm = player[turn - 1].nextMove(board, new Point(-1, -1));

                if (nm.x == -1 && nm.y == -1) {
                    // 念のため（通常はここには来ないはず）
                    MessageDialog("相手はパスです");
                    changeTurn();
                    repaint();
                    return;
                }

                board.placeAndFlip(nm.x, nm.y, color);

                int blackCount = board.countStone(Stone.black);
                int whiteCount = board.countStone(Stone.white);

                board.evaluateBoard();

                boolean isBoardFull = (blackCount + whiteCount == 64);
                boolean noMovesBoth = (board.num_grid_black == 0 && board.num_grid_white == 0);
                if (isBoardFull || noMovesBoth) {
                    repaint();
                    SwingUtilities.invokeLater(() -> EndMessageDialog());
                    return;
                }

                changeTurn();
                repaint();

            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }
}

// ===== Stone クラス =====

class Stone {
    public final static int black = 1;
    public final static int white = 2;
    private int obverse;

    Stone() {
        obverse = 0;
    }

    void setObverse(int color) {
        if (color == black || color == white) {
            obverse = color;
        } else if (color == 0) {
            obverse = 0; // 空マス
        }
    }

    int getObverse() {
        return obverse;
    }

    void paint(Graphics g, Point p, int rad) {
        if (obverse == black) {
            g.setColor(Color.black);
            g.fillOval(p.x - rad, p.y - rad, rad * 2, rad * 2);
        } else if (obverse == white) {
            g.setColor(Color.white);
            g.fillOval(p.x - rad, p.y - rad, rad * 2, rad * 2);
        }
    }
}

// ===== Board クラス =====

class Board {
    Stone[][] stones = new Stone[8][8];
    public int num_grid_black;
    public int num_grid_white;
    Point[] direction = new Point[8];
    public int[][] eval_black = new int[8][8];
    public int[][] eval_white = new int[8][8];

    Board() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                stones[i][j] = new Stone();
            }
        }
        // 初期配置（標準オセロとは色が逆ですが、元コードに合わせています）
        stones[3][3].setObverse(Stone.black);
        stones[3][4].setObverse(Stone.white);
        stones[4][3].setObverse(Stone.white);
        stones[4][4].setObverse(Stone.black);

        // 8方向ベクトル
        direction[0] = new Point(1, 0);
        direction[1] = new Point(1, 1);
        direction[2] = new Point(0, 1);
        direction[3] = new Point(-1, 1);
        direction[4] = new Point(-1, 0);
        direction[5] = new Point(-1, -1);
        direction[6] = new Point(0, -1);
        direction[7] = new Point(1, -1);
    }

    boolean isOnBoard(int x, int y) {
        return !(x < 0 || 7 < x || y < 0 || 7 < y);
    }

    // (x,y) に s を置いた場合に反転できる石の数
    int countReverseStone(int x, int y, int s) {
        if (stones[x][y].getObverse() != 0) return 0; // すでに石あり

        int enemy = (s == Stone.black) ? Stone.white : Stone.black;
        int total = 0;

        for (int d = 0; d < 8; d++) {
            int cx = x + direction[d].x;
            int cy = y + direction[d].y;
            int cnt = 0;

            while (isOnBoard(cx, cy) && stones[cx][cy].getObverse() == enemy) {
                cnt++;
                cx += direction[d].x;
                cy += direction[d].y;
            }

            if (cnt > 0 && isOnBoard(cx, cy)
                    && stones[cx][cy].getObverse() == s) {
                total += cnt;
            }
        }
        return total;
    }

    // 盤面評価：各マスの合法手と、その数
    void evaluateBoard() {
        num_grid_black = 0;
        num_grid_white = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                eval_black[i][j] = countReverseStone(i, j, Stone.black);
                if (eval_black[i][j] > 0) num_grid_black++;

                eval_white[i][j] = countReverseStone(i, j, Stone.white);
                if (eval_white[i][j] > 0) num_grid_white++;
            }
        }
    }

    // 石を置くだけ（直接）
    void setStone(int x, int y, int s) {
        if (0 <= x && x < 8 && 0 <= y && y < 8) {
            stones[x][y].setObverse(s);
        }
    }

    // 石を置いて、挟んだ相手石をすべて反転する
    void placeAndFlip(int x, int y, int s) {
        stones[x][y].setObverse(s);

        int enemy = (s == Stone.black) ? Stone.white : Stone.black;

        for (int d = 0; d < 8; d++) {
            int dx = direction[d].x;
            int dy = direction[d].y;
            int cx = x + dx;
            int cy = y + dy;
            int cnt = 0;

            // 相手石の列をたどる
            while (isOnBoard(cx, cy) && stones[cx][cy].getObverse() == enemy) {
                cnt++;
                cx += dx;
                cy += dy;
            }

            // その先に自分の石があって、途中に相手石が1個以上あるなら反転
            if (cnt > 0 && isOnBoard(cx, cy)
                    && stones[cx][cy].getObverse() == s) {
                int fx = x + dx;
                int fy = y + dy;
                while (fx != cx || fy != cy) {
                    stones[fx][fy].setObverse(s);
                    fx += dx;
                    fy += dy;
                }
            }
        }
    }

    int countStone(int color) {
        int cnt = 0;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (stones[i][j].getObverse() == color) {
                    cnt++;
                }
            }
        }
        return cnt;
    }

    void paint(Graphics g, int unit_size) {
        // 背景（黒）
        g.setColor(Color.black);
        g.fillRect(0, 0, unit_size * 10, unit_size * 10);

        // 盤面（緑）
        g.setColor(new Color(0, 85, 0));
        g.fillRect(unit_size, unit_size, unit_size * 8, unit_size * 8);

        // 横線
        g.setColor(Color.black);
        for (int i = 0; i < 9; i++) {
            g.drawLine(unit_size, unit_size * (i + 1),
                    unit_size * 9, unit_size * (i + 1));
        }

        // 縦線
        for (int i = 0; i < 9; i++) {
            g.drawLine(unit_size * (i + 1), unit_size,
                    unit_size * (i + 1), unit_size * 9);
        }

        // 目印（黒四角）
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                g.fillRect(unit_size * (3 + 4 * i) - 5,
                        unit_size * (3 + 4 * j) - 5, 10, 10);
            }
        }

        // 石の描画
        int rad = (int) (unit_size * 0.4);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                Point center = new Point(
                        unit_size * (i + 1) + unit_size / 2,
                        unit_size * (j + 1) + unit_size / 2
                );
                stones[i][j].paint(g, center, rad);
            }
        }
    }
}

// ===== Player クラス =====

class Player {
    public final static int type_human = 0;
    public final static int type_computer = 1;

    public final static int STRATEGY_RANDOM = 1;
    public final static int STRATEGY_GREEDY = 2;
    public final static int STRATEGY_WEIGHTED = 3;
    
    private static final int[][] POSITION_WEIGHT = {
        {100, -20, 10,  5,  5, 10, -20, 100},
        {-20, -50, -2, -2, -2, -2, -50, -20},
        { 10,  -2,  5,  1,  1,  5,  -2,  10},
        {  5,  -2,  1,  0,  0,  1,  -2,   5},
        {  5,  -2,  1,  0,  0,  1,  -2,   5},
        { 10,  -2,  5,  1,  1,  5,  -2,  10},
        {-20, -50, -2, -2, -2, -2, -50, -20},
        {100, -20, 10,  5,  5, 10, -20, 100}
    };

    private int color;
    private int type;
    private int strategy;  // 1,2,3
    private Random rnd = new Random();

    Player(int c, int t, int strategy) {
        if (c == Stone.black || c == Stone.white) {
            color = c;
        } else {
            System.out.println("プレイヤーの石は黒か白でなければいけません:" + c);
            System.exit(0);
        }
        if (t == type_human || t == type_computer) {
            type = t;
        } else {
            System.out.println("プレイヤーは人間かコンピュータでなければいけません:" + t);
            System.exit(0);
        }

        if (strategy < 1 || strategy > 3) {
            this.strategy = STRATEGY_RANDOM;
        } else {
            this.strategy = strategy;
        }
    }

    int getColor() { return color; }
    int getType() { return type; }

    // とりあえず最初に見つかった合法手を返すだけの単純戦略
    Point tactics(Board bd) {
        if (strategy == STRATEGY_GREEDY) {
            return tacticsGreedy(bd);
        } else if (strategy == STRATEGY_WEIGHTED) {
            return tacticsWeighted(bd);
        } else { // STRATEGY_RANDOM など
            return tacticsRandom(bd);
        }
    }

    Point nextMove(Board bd, Point p) {
        if (type == type_human) {
            return p;
        } else if (type == type_computer) {
            return tactics(bd);
        }
        return (new Point(-1, -1));
    }

    private Point tacticsRandom(Board bd) {
        List<Point> legalMoves = collectLegalMoves(bd);
        if (legalMoves.isEmpty()) {
            return new Point(-1, -1); // パス
        }
        int idx = rnd.nextInt(legalMoves.size());
        return legalMoves.get(idx);
    }

    private Point tacticsGreedy(Board bd) {
        List<Point> candidates = new ArrayList<>();
        int bestFlips = -1;

        // eval_* には「そのマスに置いたとき何個ひっくり返せるか」が入っている
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int flips = (color == Stone.black)
                        ? bd.eval_black[x][y]
                        : bd.eval_white[x][y];

                if (flips <= 0) continue; // 合法手でない

                if (flips > bestFlips) {
                    bestFlips = flips;
                    candidates.clear();
                    candidates.add(new Point(x, y));
                } else if (flips == bestFlips) {
                    candidates.add(new Point(x, y));
                }
            }
        }

        if (candidates.isEmpty()) {
            return new Point(-1, -1); // パス
        }

        // 最大値を取る候補の中からランダムに 1 つ選ぶ
        int idx = rnd.nextInt(candidates.size());
        return candidates.get(idx);
    }

    // ==== ③ 盤面考慮戦略 ====
    // flips の数 + 盤面重み を評価値として最大のマスを選択（同点ならランダム）
    private Point tacticsWeighted(Board bd) {
        List<Point> candidates = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;

        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int flips = (color == Stone.black)
                        ? bd.eval_black[x][y]
                        : bd.eval_white[x][y];

                if (flips <= 0) continue; // 合法手でない

                int weight = POSITION_WEIGHT[y][x];     // y が行, x が列
                int score = flips + weight;             // flips に重みを足した簡単な評価

                if (score > bestScore) {
                    bestScore = score;
                    candidates.clear();
                    candidates.add(new Point(x, y));
                } else if (score == bestScore) {
                    candidates.add(new Point(x, y));
                }
            }
        }

        if (candidates.isEmpty()) {
            return new Point(-1, -1); // パス
        }

        int idx = rnd.nextInt(candidates.size());
        return candidates.get(idx);
    }

    private List<Point> collectLegalMoves(Board bd) {
        List<Point> res = new ArrayList<>();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int flips = (color == Stone.black)
                        ? bd.eval_black[x][y]
                        : bd.eval_white[x][y];
                if (flips > 0) {
                    res.add(new Point(x, y));
                }
            }
        }
        return res;
    }
}

