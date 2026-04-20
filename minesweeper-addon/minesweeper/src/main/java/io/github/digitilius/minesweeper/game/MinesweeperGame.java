/*
 * Copyright 2026 Stanislav Makarov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.digitilius.minesweeper.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Pure game state: mine placement, reveal with flood-fill, flags, win/lose.
 */
public final class MinesweeperGame {

    public enum Phase {
        NOT_STARTED,
        PLAYING,
        WON,
        LOST
    }

    public enum RevealOutcome {
        IGNORED,
        REVEALED,
        HIT_MINE
    }

    /**
     * Board coordinate (column, row).
     */
    public record Cell(int col, int row) {
    }

    /**
     * Result of {@link #reveal(int, int)}: outcome and cells that became revealed this step.
     */
    public record RevealDelta(RevealOutcome outcome, List<Cell> changedCells) {
        public RevealDelta {
            changedCells = List.copyOf(changedCells);
        }
    }

    private final int cols;
    private final int rows;
    private boolean[][] mines;
    private final boolean[][] revealed;
    private final boolean[][] flagged;
    private Phase phase = Phase.NOT_STARTED;
    /** Cell whose reveal triggered loss; {@code -1} if none. */
    private int fatalCol = -1;
    private int fatalRow = -1;

    public MinesweeperGame(int cols, int rows) {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("Field dimensions must be positive");
        }
        this.cols = cols;
        this.rows = rows;
        this.revealed = new boolean[rows][cols];
        this.flagged = new boolean[rows][cols];
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isMine(int col, int row) {
        return mines != null && mines[row][col];
    }

    public boolean isRevealed(int col, int row) {
        return revealed[row][col];
    }

    public boolean isFlagged(int col, int row) {
        return flagged[row][col];
    }

    public boolean isFatalHit(int col, int row) {
        return phase == Phase.LOST && fatalCol == col && fatalRow == row;
    }

    public int countMines() {
        if (mines == null) {
            return 0;
        }
        int n = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mines[r][c]) {
                    n++;
                }
            }
        }
        return n;
    }

    public int countFlaggedCells() {
        int n = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (flagged[r][c]) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Places mines and begins play. Clears prior reveal/flag state.
     */
    public void start(double mineProbability, Random random) {
        if (mineProbability < 0 || mineProbability > 1) {
            throw new IllegalArgumentException("mineProbability must be in [0,1]");
        }
        fatalCol = -1;
        fatalRow = -1;
        clearArrays();
        this.mines = placeMines(cols, rows, mineProbability, random);
        phase = Phase.PLAYING;
    }

    /**
     * Back to covered field without mines; Start must be pressed again.
     */
    public void resetToNotStarted() {
        mines = null;
        fatalCol = -1;
        fatalRow = -1;
        clearArrays();
        phase = Phase.NOT_STARTED;
    }

    private void clearArrays() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                revealed[r][c] = false;
                flagged[r][c] = false;
            }
        }
    }

    static boolean[][] placeMines(int cols, int rows, double p, Random random) {
        boolean[][] m = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                m[r][c] = random.nextDouble() < p;
            }
        }
        // Ensure at least one safe cell
        boolean allMines = true;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!m[r][c]) {
                    allMines = false;
                    break outer;
                }
            }
        }
        if (allMines && rows * cols > 0) {
            m[random.nextInt(rows)][random.nextInt(cols)] = false;
        }
        return m;
    }

    public RevealDelta reveal(int col, int row) {
        if (phase != Phase.PLAYING || mines == null) {
            return new RevealDelta(RevealOutcome.IGNORED, List.of());
        }
        if (flagged[row][col] || revealed[row][col]) {
            return new RevealDelta(RevealOutcome.IGNORED, List.of());
        }
        if (mines[row][col]) {
            fatalCol = col;
            fatalRow = row;
            phase = Phase.LOST;
            return new RevealDelta(RevealOutcome.HIT_MINE, revealAllMinesAndCollect());
        }
        List<Cell> changed = floodRevealAndCollect(col, row);
        if (allSafeRevealed()) {
            phase = Phase.WON;
        }
        return new RevealDelta(RevealOutcome.REVEALED, changed);
    }

    /**
     * Toggle flag on a covered cell. Returns true if state changed.
     */
    public boolean toggleFlag(int col, int row) {
        if (phase != Phase.PLAYING || mines == null) {
            return false;
        }
        if (revealed[row][col]) {
            return false;
        }
        flagged[row][col] = !flagged[row][col];
        return true;
    }

    public int neighborMineCount(int col, int row) {
        if (mines == null) {
            return 0;
        }
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nc = col + dc;
                int nr = row + dr;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && mines[nr][nc]) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<Cell> revealAllMinesAndCollect() {
        List<Cell> list = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mines[r][c]) {
                    revealed[r][c] = true;
                    list.add(new Cell(c, r));
                }
            }
        }
        return list;
    }

    private List<Cell> floodRevealAndCollect(int col, int row) {
        List<Cell> changed = new ArrayList<>();
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{col, row});
        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            int c = cell[0];
            int r = cell[1];
            if (r < 0 || r >= rows || c < 0 || c >= cols) {
                continue;
            }
            if (revealed[r][c] || flagged[r][c] || mines[r][c]) {
                continue;
            }
            revealed[r][c] = true;
            changed.add(new Cell(c, r));
            if (neighborMineCount(c, r) == 0) {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) {
                            continue;
                        }
                        stack.push(new int[]{c + dc, r + dr});
                    }
                }
            }
        }
        return changed;
    }

    private boolean allSafeRevealed() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!mines[r][c] && !revealed[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }
}
