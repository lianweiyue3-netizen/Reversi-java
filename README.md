# Java Reversi（オセロ）– AI Strategy Implementation

Java（Swing）を用いて実装したリバーシ（オセロ）ゲームです。  
**Human（黒） vs Computer（白）** の対戦をサポートし、複数の AI 戦略を切り替えて比較できる設計になっています。

本プロジェクトでは、**ゲームロジックと UI の分離**、および  
**戦略パターンを意識した AI 実装**を重視しました。

---

## 技術スタック（Tech Stack）

- 言語：Java
- GUI：Swing（`javax.swing`, `java.awt`）
- 開発環境：JDK 8+
- バージョン管理：Git / GitHub

---

## プロジェクト構成

本プロジェクトは **単一ファイル構成** ですが、  
内部的には役割ごとにクラスを分離しています。

- `Reversi`：UI 制御、ゲーム進行、イベント処理
- `Board`：盤面状態管理、合法手判定、石反転ロジック
- `Stone`：石の状態管理と描画
- `Player`：人間 / コンピュータの抽象化、戦略切替

この構成により、**戦略追加や改良を Player クラスに集中**して行える設計になっています。

---

## 起動方法（Build & Run）

### コンパイル
```bash
javac Reversi.java
```

### 実行
```bash
java Reversi
```

黒番（人間）はマウスクリックで操作します。

---

## AI 戦略（Strategy Design）

実行時の引数により、コンピュータプレイヤーの思考戦略を変更できます。
```bash
java Reversi 1
java Reversi 2
java Reversi 3
```

### 実装されている戦略

| ID | Strategy | 概要                     |
| -: | -------- | ---------------------- |
|  1 | Random   | 合法手からランダムに選択           |
|  2 | Greedy   | 1 手で裏返せる石の数を最大化        |
|  3 | Weighted | 裏返し数 + 盤面位置重み（角・辺を高評価） |

Weighted 戦略では、オセロの定石に基づき
角・辺・危険マスを考慮した評価行列（POSITION_WEIGHT）を使用しています。
