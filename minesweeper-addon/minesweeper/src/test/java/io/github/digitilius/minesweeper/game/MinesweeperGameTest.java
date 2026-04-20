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

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MinesweeperGameTest {

    @Test
    void placeMines_alwaysLeavesAtLeastOneSafeCell() {
        Random r = new Random(1);
        for (int i = 0; i < 50; i++) {
            boolean[][] m = MinesweeperGame.placeMines(3, 3, 1.0, r);
            int mines = 0;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    if (m[row][col]) {
                        mines++;
                    }
                }
            }
            assertTrue(mines < 9, "field 3x3 cannot be all mines");
        }
    }

    @Test
    void revealMine_endsGameAndShowsMines() {
        MinesweeperGame g = new MinesweeperGame(2, 2);
        g.start(0.0, new Random(0));
        Random rnd = new Random(42);
        g.resetToNotStarted();
        g.start(0.99, rnd);
        int mc = 0;
        int mineCol = -1, mineRow = -1;
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                if (g.isMine(col, row)) {
                    mc++;
                    mineCol = col;
                    mineRow = row;
                }
            }
        }
        assertTrue(mc >= 1);
        MinesweeperGame.RevealDelta d = g.reveal(mineCol, mineRow);
        assertEquals(MinesweeperGame.RevealOutcome.HIT_MINE, d.outcome());
        assertFalse(d.changedCells().isEmpty());
        assertEquals(MinesweeperGame.Phase.LOST, g.getPhase());
        assertTrue(g.isRevealed(mineCol, mineRow));
        assertTrue(g.isFatalHit(mineCol, mineRow));
        assertEquals(g.countMines(), d.changedCells().size());
    }

    @Test
    void floodReveal_opensAdjacentZeros() {
        MinesweeperGame g = new MinesweeperGame(3, 1);
        g.start(0.0, new Random(0));
        MinesweeperGame.RevealDelta d = g.reveal(1, 0);
        assertEquals(MinesweeperGame.RevealOutcome.REVEALED, d.outcome());
        assertEquals(3, d.changedCells().size());
        assertTrue(g.isRevealed(0, 0));
        assertTrue(g.isRevealed(1, 0));
        assertTrue(g.isRevealed(2, 0));
    }

    @Test
    void flagBlocksReveal() {
        MinesweeperGame g = new MinesweeperGame(2, 2);
        g.start(0.0, new Random(0));
        assertTrue(g.toggleFlag(0, 0));
        MinesweeperGame.RevealDelta d = g.reveal(0, 0);
        assertEquals(MinesweeperGame.RevealOutcome.IGNORED, d.outcome());
        assertTrue(d.changedCells().isEmpty());
        assertFalse(g.isRevealed(0, 0));
    }

    @Test
    void win_whenAllSafeRevealed() {
        MinesweeperGame g = new MinesweeperGame(2, 2);
        g.start(0.0, new Random(0));
        g.reveal(0, 0);
        g.reveal(1, 0);
        g.reveal(0, 1);
        g.reveal(1, 1);
        assertEquals(MinesweeperGame.Phase.WON, g.getPhase());
    }

    @Test
    void notStarted_revealIgnored() {
        MinesweeperGame g = new MinesweeperGame(2, 2);
        MinesweeperGame.RevealDelta d = g.reveal(0, 0);
        assertEquals(MinesweeperGame.RevealOutcome.IGNORED, d.outcome());
        assertTrue(d.changedCells().isEmpty());
    }

    @Test
    void countMines_matchesBoard() {
        MinesweeperGame g = new MinesweeperGame(4, 4);
        g.start(1.0, new Random(0));
        int fromApi = g.countMines();
        int manual = 0;
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (g.isMine(c, r)) {
                    manual++;
                }
            }
        }
        assertEquals(manual, fromApi);
        assertTrue(fromApi >= 1 && fromApi < 16);
    }

    @Test
    void countFlaggedCells_tracksToggles() {
        MinesweeperGame g = new MinesweeperGame(2, 2);
        g.start(0.0, new Random(0));
        assertEquals(0, g.countFlaggedCells());
        assertTrue(g.toggleFlag(0, 0));
        assertEquals(1, g.countFlaggedCells());
        assertTrue(g.toggleFlag(0, 0));
        assertEquals(0, g.countFlaggedCells());
    }

    @Test
    void isFatalHit_onlyOnTriggeredMine() {
        MinesweeperGame g = new MinesweeperGame(2, 2);
        g.start(0.99, new Random(1));
        int mineC = -1, mineR = -1;
        outer:
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                if (g.isMine(c, r)) {
                    mineC = c;
                    mineR = r;
                    break outer;
                }
            }
        }
        assertTrue(mineC >= 0);
        g.reveal(mineC, mineR);
        assertTrue(g.isFatalHit(mineC, mineR));
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                if (c != mineC || r != mineR) {
                    assertFalse(g.isFatalHit(c, r));
                }
            }
        }
    }

    @Test
    void singleCellReveal_deltaContainsOneCellWhenNoFlood() {
        MinesweeperGame g = new MinesweeperGame(1, 1);
        g.start(0.0, new Random(0));
        MinesweeperGame.RevealDelta d = g.reveal(0, 0);
        assertEquals(MinesweeperGame.RevealOutcome.REVEALED, d.outcome());
        assertEquals(1, d.changedCells().size());
        assertEquals(new MinesweeperGame.Cell(0, 0), d.changedCells().get(0));
    }
}
