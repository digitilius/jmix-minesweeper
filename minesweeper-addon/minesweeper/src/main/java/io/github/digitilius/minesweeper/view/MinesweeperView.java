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
package io.github.digitilius.minesweeper.view;

import io.github.digitilius.minesweeper.game.MinesweeperGame;
import io.github.digitilius.minesweeper.game.MinesweeperProperties;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.textfield.IntegerField;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.view.DefaultMainViewParent;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Route(value = "minesweeper", layout = DefaultMainViewParent.class)
@ViewController(id = "minesweeper_Minesweeper.view")
@ViewDescriptor(path = "minesweeper-view.xml")
public class MinesweeperView extends StandardView {

    private static final int REVEAL_DEFER_MS = 280;
    private static final int FIELD_MAX = 99;
    private static final int PROBABILITY_MIN = 0;
    private static final int PROBABILITY_MAX = 100;
    private static final int PERCENT_SCALE = 100;
    private static final int HUD_TIMER_MAX = 999;
    private static final int HUD_MINES_MAX = 999;
    private static final int HUD_MINES_NEG_MAX = 99;

    private static final String CELL_SIZE = "28px";
    private static final String THEME_STYLESHEET = "themes/minesweeper/minesweeper-view.css";
    private static final String SCHEDULER_THREAD_NAME = "minesweeper-scheduler";

    private static final String MSG_VIEW = "MinesweeperView.";

    private static final String MAIN_GAME_PANEL_BG = "var(--minesweeper-game-panel, #EBEBEB)";
    private static final String FIELD_BG = "var(--minesweeper-field, #EBEDF0)";
    private static final String OPENED_CELL_BG = "var(--minesweeper-opened, #C0C0C0)";
    private static final String FATAL_MINE_BG = "var(--minesweeper-fatal, #ff0000)";
    private static final String TOP_PANEL_BG = "var(--minesweeper-top-panel, #D6D6D6)";
    private static final String FLAG_COLOR_HEX = "#B01000";
    private static final String BOMB_ICON_COLOR = "#000000";
    private static final String COVERED_BUTTON_TEXT = "#101010";
    private static final String COVERED_BUTTON_DISABLED_BG = "#e4e6ea";
    private static final String COVERED_BUTTON_DISABLED_BORDER = "2px outset #c8c8c8";
    private static final String BORDER_OUTSET_CELL = "2px outset #dedede";
    private static final String BORDER_INSET_SPAN = "1px inset #9e9e9e";
    private static final String BORDER_OUTSET_PANEL = "2px outset #dedede";
    private static final String BORDER_GROOVE_TOOLBAR = "2px groove #bdbdbd";
    private static final String BORDER_INSET_BOARD = "3px inset #bdbdbd";
    private static final String LED_TEXT = "#FC1C00";
    private static final String LED_BG = "#200000";
    private static final String LED_BORDER = "1px inset #333";

    private static final String GRID_GAP = "2px";
    private static final String BOARD_PADDING = "3px";
    private static final String PANEL_PADDING = "0.35rem";
    private static final String TOOLBAR_PADDING = "0.25rem 0.35rem";
    private static final String TOOLBAR_MARGIN_BOTTOM = "0.35rem";

    private static final String SMILE_PLAYING = "🙂";
    private static final String SMILE_LOST = "☹️";
    private static final String SMILE_WON = "😎";

    private static final String CLASS_CELL_WRAP = "minesweeper-cell-wrap";
    private static final String CLASS_GAME_BOARD = "minesweeper-game-board";
    private static final String CLASS_CELL_BTN = "minesweeper-cell-btn";
    private static final String CLASS_CELL_SPAN = "minesweeper-cell-span";
    private static final String CLASS_FLAG = "minesweeper-flag";
    private static final String CLASS_MINE_ICON = "minesweeper-mine-icon";
    private static final String CLASS_DIGIT_PREFIX = "minesweeper-digit-";
    private static final String CLASS_LED_SEGMENT = "minesweeper-led-segment";

    private static final String[] DIGIT_HEX_COLORS = {
            null,
            "#0000ff", "#008000", "#ff0000", "#654321", "#000000",
            "#008080", "#000000", "#808080"
    };

    @Autowired
    private MinesweeperProperties properties;
    @Autowired
    private MessageBundle messageBundle;
    @Autowired
    private Notifications notifications;

    @ViewComponent
    private Button showRulesButton;
    @ViewComponent
    private IntegerField widthField;
    @ViewComponent
    private IntegerField heightField;
    @ViewComponent
    private IntegerField probabilityField;
    @ViewComponent
    private NativeLabel helpLabel;
    @ViewComponent
    private Div gameGridHost;
    @ViewComponent
    private Div fieldSizePresetHost;

    private RadioButtonGroup<FieldSizePreset> fieldSizePresetGroup;

    private MinesweeperGame game;
    private final List<Div> cellWraps = new ArrayList<>();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingRevealFuture;
    private ScheduledFuture<?> elapsedFuture;
    private int pendingCol = -1;
    private int pendingRow = -1;
    private int elapsedSeconds;
    private boolean winNotificationShown;
    private Span toolbarMineCounter;
    private Span toolbarTimer;
    private Button smileButton;
    private boolean stylesheetAdded;
    private boolean rulesPanelVisible;
    private boolean timerStartedForCurrentGame;

    private String msgView(final String name) {
        return messageBundle.getMessage(MSG_VIEW + name);
    }

    private void initFieldSizePresetControls() {
        fieldSizePresetGroup = new RadioButtonGroup<>();
        fieldSizePresetGroup.setLabel(msgView("fieldSize"));
        fieldSizePresetGroup.setItems(FieldSizePreset.values());
        fieldSizePresetGroup.setItemLabelGenerator(this::fieldSizePresetLabel);
        fieldSizePresetGroup.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        fieldSizePresetGroup.setWidthFull();
        fieldSizePresetGroup.addValueChangeListener(e -> {
            FieldSizePreset preset = e.getValue();
            if (preset != null) {
                applyFieldSizePreset(preset);
            }
        });
        fieldSizePresetHost.removeAll();
        fieldSizePresetHost.add(fieldSizePresetGroup);

        fieldSizePresetGroup.setValue(FieldSizePreset.BEGINNER);
    }

    private void applyFieldSizePreset(final FieldSizePreset preset) {
        boolean custom = preset.isCustom();
        widthField.setVisible(custom);
        heightField.setVisible(custom);
        if (!custom) {
            widthField.setValue(preset.cols);
            heightField.setValue(preset.rows);
        }
        beginFreshGame();
    }

    private String fieldSizePresetLabel(final FieldSizePreset preset) {
        return msgView(preset.messageKey);
    }

    private enum FieldSizePreset {
        BEGINNER(9, 9, "beginner"),
        INTERMEDIATE(16, 16, "intermediate"),
        EXPERT(32, 32, "expert"),
        CUSTOM(0, 0, "custom");

        private final int cols;
        private final int rows;
        private final String messageKey;

        FieldSizePreset(final int cols, final int rows, final String messageKey) {
            this.cols = cols;
            this.rows = rows;
            this.messageKey = messageKey;
        }

        boolean isCustom() {
            return this == CUSTOM;
        }
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        getContent().setSizeFull();
        widthField.setMin(1);
        widthField.setMax(FIELD_MAX);
        heightField.setMin(1);
        heightField.setMax(FIELD_MAX);
        probabilityField.setMin(PROBABILITY_MIN);
        probabilityField.setMax(PROBABILITY_MAX);

        widthField.setValue(FieldSizePreset.BEGINNER.cols);
        heightField.setValue(FieldSizePreset.BEGINNER.rows);
        probabilityField.setValue(properties.getMineProbabilityPercent());

        initFieldSizePresetControls();

        helpLabel.setText(msgView("help") + "\n\n" + msgView("actions.available"));

        rulesPanelVisible = false;
        helpLabel.setVisible(false);
        showRulesButton.setText(msgView("showRules"));
    }

    @Subscribe("showRulesButton")
    public void onShowRulesButtonClick(final ClickEvent<Button> event) {
        if (!event.isFromClient()) {
            return;
        }
        rulesPanelVisible = !rulesPanelVisible;
        helpLabel.setVisible(rulesPanelVisible);
        showRulesButton.setText(rulesPanelVisible ? msgView("hideRules") : msgView("showRules"));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (!stylesheetAdded) {
            attachEvent.getUI().getPage().addStyleSheet(THEME_STYLESHEET);
            stylesheetAdded = true;
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        cancelPendingReveal();
        stopElapsedTimer();
        shutdownScheduler();
        super.onDetach(detachEvent);
    }

    private void beginFreshGame() {
        cancelPendingReveal();
        stopElapsedTimer();
        int cols = readPositiveInt(widthField, properties.getWidth());
        int rows = readPositiveInt(heightField, properties.getEffectiveHeight());
        int prob = readProbability(probabilityField, properties.getMineProbabilityPercent());
        widthField.setValue(cols);
        heightField.setValue(rows);
        probabilityField.setValue(prob);
        game = new MinesweeperGame(cols, rows);
        game.start(prob / (double) PERCENT_SCALE, new Random());
        winNotificationShown = false;
        elapsedSeconds = 0;
        timerStartedForCurrentGame = false;
        buildPlayingBoard();
        refreshAllCells();
        updateHud();
    }

    private int readPositiveInt(IntegerField field, int fallback) {
        Integer v = field.getValue();
        if (v == null || v < 1) {
            return Math.max(1, fallback);
        }
        return Math.min(FIELD_MAX, v);
    }

    private int readProbability(IntegerField field, int fallback) {
        Integer v = field.getValue();
        if (v == null) {
            return Math.max(PROBABILITY_MIN, Math.min(PROBABILITY_MAX, fallback));
        }
        return Math.max(PROBABILITY_MIN, Math.min(PROBABILITY_MAX, v));
    }

    private void applyGameGridHostChrome() {
        gameGridHost.getStyle().set("display", "flex");
        gameGridHost.getStyle().set("flex-direction", "column");
        gameGridHost.getStyle().set("align-items", "center");
        gameGridHost.getStyle().set("width", "100%");
        gameGridHost.getStyle().set("box-sizing", "border-box");
        gameGridHost.getStyle().set("padding", PANEL_PADDING);
        gameGridHost.getStyle().set("background-color", MAIN_GAME_PANEL_BG);
        gameGridHost.getStyle().set("border", BORDER_OUTSET_PANEL);
    }

    private void buildPlayingBoard() {
        gameGridHost.removeAll();
        cellWraps.clear();
        gameGridHost.setVisible(true);
        applyGameGridHostChrome();
        gameGridHost.add(buildGameToolbar());

        Div board = new Div();
        board.addClassNames(CLASS_GAME_BOARD);
        int cols = game.getCols();
        int rows = game.getRows();
        board.getStyle().set("display", "grid");
        board.getStyle().set("grid-template-columns", "repeat(" + cols + ", minmax(" + CELL_SIZE + ", " + CELL_SIZE + "))");
        board.getStyle().set("grid-auto-rows", CELL_SIZE);
        board.getStyle().set("gap", GRID_GAP);
        board.getStyle().set("width", "max-content");
        board.getStyle().set("max-width", "100%");
        board.getStyle().set("box-sizing", "border-box");
        board.getStyle().set("border", BORDER_INSET_BOARD);
        board.getStyle().set("padding", BOARD_PADDING);
        board.getStyle().set("background-color", FIELD_BG);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Div wrap = new Div();
                wrap.addClassName(CLASS_CELL_WRAP);
                applyCellWrapLayout(wrap);
                replaceCellContent(wrap, c, r);
                board.add(wrap);
                cellWraps.add(wrap);
            }
        }
        gameGridHost.add(board);
    }

    private Div buildGameToolbar() {
        Div bar = new Div();
        bar.getStyle().set("display", "flex");
        bar.getStyle().set("flex-direction", "row");
        bar.getStyle().set("align-items", "center");
        bar.getStyle().set("justify-content", "space-between");
        bar.getStyle().set("width", "100%");
        bar.getStyle().set("max-width", "100%");
        bar.getStyle().set("box-sizing", "border-box");
        bar.getStyle().set("padding", TOOLBAR_PADDING);
        bar.getStyle().set("margin-bottom", TOOLBAR_MARGIN_BOTTOM);
        bar.getStyle().set("background-color", TOP_PANEL_BG);
        bar.getStyle().set("border", BORDER_GROOVE_TOOLBAR);

        toolbarMineCounter = new Span(formatMinesCounter());
        styleLedSegment(toolbarMineCounter);

        smileButton = new Button(SMILE_PLAYING);
        smileButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        smileButton.getStyle().set("min-width", "2.25rem");
        smileButton.getStyle().set("height", "2.25rem");
        smileButton.getStyle().set("padding", "0");
        smileButton.getStyle().set("border", BORDER_OUTSET_PANEL);
        smileButton.getStyle().set("background-color", TOP_PANEL_BG);
        smileButton.getStyle().set("border-radius", "2px");
        smileButton.getStyle().set("font-size", "1.25rem");
        smileButton.getStyle().set("line-height", "1");
        smileButton.addClickListener(ev -> {
            if (ev.isFromClient()) {
                beginFreshGame();
            }
        });

        toolbarTimer = new Span(formatTimeDigits(elapsedSeconds));
        styleLedSegment(toolbarTimer);

        bar.add(toolbarMineCounter, smileButton, toolbarTimer);
        return bar;
    }

    private String formatMinesCounter() {
        if (game == null) {
            return "000";
        }
        int mines = game.countMines();
        int flags = game.countFlaggedCells();
        int left = mines - flags;
        if (left < 0) {
            return "-" + String.format("%02d", Math.min(HUD_MINES_NEG_MAX, -left));
        }
        return String.format("%03d", Math.min(HUD_MINES_MAX, left));
    }

    private static String formatTimeDigits(final int seconds) {
        return String.format("%03d", Math.min(HUD_TIMER_MAX, Math.max(0, seconds)));
    }

    private ScheduledExecutorService scheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, SCHEDULER_THREAD_NAME);
                t.setDaemon(true);
                return t;
            });
        }
        return scheduler;
    }

    private void shutdownScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void startElapsedTimer() {
        stopElapsedTimer();
        elapsedFuture = scheduler().scheduleAtFixedRate(() -> {
            UI ui = getUI().orElse(null);
            if (ui == null) {
                return;
            }
            ui.access(() -> {
                if (game == null || game.getPhase() != MinesweeperGame.Phase.PLAYING) {
                    return;
                }
                elapsedSeconds++;
                if (toolbarTimer != null) {
                    toolbarTimer.setText(formatTimeDigits(elapsedSeconds));
                }
            });
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void stopElapsedTimer() {
        if (elapsedFuture != null) {
            elapsedFuture.cancel(false);
            elapsedFuture = null;
        }
    }

    private void updateHud() {
        if (toolbarMineCounter != null) {
            toolbarMineCounter.setText(formatMinesCounter());
        }
        if (toolbarTimer != null) {
            toolbarTimer.setText(formatTimeDigits(elapsedSeconds));
        }
        updateSmiley();
    }

    private void updateSmiley() {
        if (smileButton == null || game == null) {
            return;
        }
        smileButton.setText(switch (game.getPhase()) {
            case WON -> SMILE_WON;
            case LOST -> SMILE_LOST;
            default -> SMILE_PLAYING;
        });
    }

    private void notifyWinIfNeeded() {
        if (game.getPhase() != MinesweeperGame.Phase.WON || winNotificationShown) {
            return;
        }
        winNotificationShown = true;
        notifications.create(msgView("winNotification"))
                .withThemeVariant(NotificationVariant.LUMO_SUCCESS)
                .withPosition(Notification.Position.TOP_END)
                .show();
    }

    private static void styleLedSegment(Span span) {
        span.addClassName(CLASS_LED_SEGMENT);
        span.getStyle().set("font-family", "ui-monospace, monospace");
        span.getStyle().set("font-size", "1.35rem");
        span.getStyle().set("letter-spacing", "0.08em");
        span.getStyle().set("color", LED_TEXT);
        span.getStyle().set("background", LED_BG);
        span.getStyle().set("padding", "0.1rem 0.35rem");
        span.getStyle().set("border", LED_BORDER);
        span.getStyle().set("min-width", "3.2rem");
        span.getStyle().set("text-align", "right");
    }

    private static void applyCellWrapLayout(final Div wrap) {
        wrap.getStyle().set("width", CELL_SIZE);
        wrap.getStyle().set("height", CELL_SIZE);
        wrap.getStyle().set("box-sizing", "border-box");
        wrap.getStyle().set("padding", "0");
        wrap.getStyle().set("margin", "0");
    }

    private void applyCoveredOrSpanVisual(final Component cell, final int col, final int row) {
        if (cell instanceof Button b) {
            applyCoveredButtonVisual(b);
        } else if (cell instanceof Span s) {
            applyOpenedSpanVisual(s, col, row);
        }
    }

    private void applyCoveredButtonVisual(Button b) {
        b.getStyle().set("width", CELL_SIZE);
        b.getStyle().set("height", CELL_SIZE);
        b.getStyle().set("min-width", CELL_SIZE);
        b.getStyle().set("min-height", CELL_SIZE);
        b.getStyle().set("padding", "0");
        b.getStyle().set("margin", "0");
        b.getStyle().set("box-sizing", "border-box");
        b.getStyle().set("border", BORDER_OUTSET_CELL);
        b.getStyle().set("border-radius", "0");
        b.getStyle().set("color", COVERED_BUTTON_TEXT);
        if (b.isEnabled()) {
            b.getStyle().set("background-color", FIELD_BG);
        } else {
            b.getStyle().set("background-color", COVERED_BUTTON_DISABLED_BG);
            b.getStyle().set("border", COVERED_BUTTON_DISABLED_BORDER);
            b.getStyle().set("opacity", "1");
        }
    }

    private void applyOpenedSpanVisual(Span s, int col, int row) {
        s.getStyle().set("width", CELL_SIZE);
        s.getStyle().set("height", CELL_SIZE);
        s.getStyle().set("min-width", CELL_SIZE);
        s.getStyle().set("min-height", CELL_SIZE);
        s.getStyle().set("display", "flex");
        s.getStyle().set("align-items", "center");
        s.getStyle().set("justify-content", "center");
        s.getStyle().set("box-sizing", "border-box");
        s.getStyle().set("border", BORDER_INSET_SPAN);
        s.getStyle().set("border-radius", "0");
        s.getStyle().set("font-weight", "700");
        if (game != null && game.isRevealed(col, row) && game.isMine(col, row) && game.isFatalHit(col, row)) {
            s.getStyle().set("background-color", FATAL_MINE_BG);
        } else {
            s.getStyle().set("background-color", OPENED_CELL_BG);
        }
    }

    private Component cellComponentForState(int col, int row) {
        if (game.isRevealed(col, row)) {
            Span span = new Span();
            span.addClassName(CLASS_CELL_SPAN);
            if (game.isMine(col, row)) {
                Icon bomb = new Icon(VaadinIcon.BOMB);
                bomb.addClassName(CLASS_MINE_ICON);
                bomb.setColor(BOMB_ICON_COLOR);
                bomb.getStyle().set("width", "1.05rem");
                bomb.getStyle().set("height", "1.05rem");
                span.add(bomb);
            } else {
                int n = game.neighborMineCount(col, row);
                if (n > 0) {
                    span.setText(String.valueOf(n));
                    applyDigitClass(span, n);
                }
            }
            span.getElement().setAttribute("tabindex", "0");
            return span;
        }
        Button covered = newButtonCell(col, row);
        if (game.getPhase() == MinesweeperGame.Phase.LOST || game.getPhase() == MinesweeperGame.Phase.WON) {
            covered.setEnabled(false);
        }
        return covered;
    }

    private void applyDigitClass(Span span, int n) {
        for (int d = 1; d <= 8; d++) {
            span.removeClassName(CLASS_DIGIT_PREFIX + d);
        }
        if (n >= 1 && n <= 8) {
            span.addClassName(CLASS_DIGIT_PREFIX + n);
            span.getStyle().set("color", DIGIT_HEX_COLORS[n]);
        } else {
            span.getStyle().remove("color");
        }
    }

    private Button newButtonCell(int col, int row) {
        Button b = new Button();
        b.addClassName(CLASS_CELL_BTN);
        b.addThemeVariants(ButtonVariant.LUMO_SMALL);
        b.setTabIndex(0);

        if (game.isFlagged(col, row)) {
            Icon icon = new Icon(VaadinIcon.FLAG);
            icon.addClassName(CLASS_FLAG);
            icon.setColor(FLAG_COLOR_HEX);
            b.setIcon(icon);
        }

        b.addClickListener(ev -> {
            if (!ev.isFromClient()) {
                return;
            }
            if (game.getPhase() != MinesweeperGame.Phase.PLAYING) {
                return;
            }
            if (ev.getClickCount() == 2) {
                cancelPendingReveal();
                if (game.toggleFlag(col, row)) {
                    refreshCell(col, row);
                }
                return;
            }
            scheduleReveal(col, row, ev.getSource().getUI().orElse(null));
        });

        return b;
    }

    private void scheduleReveal(int col, int row, UI ui) {
        if (game.getPhase() != MinesweeperGame.Phase.PLAYING) {
            return;
        }
        if (game.isFlagged(col, row) || game.isRevealed(col, row)) {
            return;
        }
        cancelPendingReveal();
        if (ui == null) {
            return;
        }
        pendingCol = col;
        pendingRow = row;
        final int scheduledCol = col;
        final int scheduledRow = row;
        pendingRevealFuture = scheduler().schedule(() -> {
            UI uiTick = getUI().orElse(null);
            if (uiTick == null) {
                return;
            }
            uiTick.access(() -> {
                try {
                    if (pendingCol == scheduledCol && pendingRow == scheduledRow) {
                        doReveal(scheduledCol, scheduledRow);
                    }
                } finally {
                    pendingRevealFuture = null;
                }
            });
        }, REVEAL_DEFER_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelPendingReveal() {
        if (pendingRevealFuture != null) {
            pendingRevealFuture.cancel(false);
            pendingRevealFuture = null;
        }
        pendingCol = -1;
        pendingRow = -1;
    }

    private void doReveal(int col, int row) {
        MinesweeperGame.RevealDelta delta = game.reveal(col, row);
        if (delta.outcome() == MinesweeperGame.RevealOutcome.IGNORED) {
            return;
        }
        if (!timerStartedForCurrentGame) {
            timerStartedForCurrentGame = true;
            startElapsedTimer();
        }
        if (game.getPhase() != MinesweeperGame.Phase.PLAYING) {
            refreshAllCells();
        } else {
            for (MinesweeperGame.Cell cell : delta.changedCells()) {
                refreshCell(cell.col(), cell.row());
            }
        }
        if (game.getPhase() == MinesweeperGame.Phase.LOST || game.getPhase() == MinesweeperGame.Phase.WON) {
            stopElapsedTimer();
            notifyWinIfNeeded();
        }
    }

    private void replaceCellContent(Div wrap, int col, int row) {
        wrap.removeAll();
        Component next = cellComponentForState(col, row);
        applyCoveredOrSpanVisual(next, col, row);
        wrap.add(next);
    }

    private void refreshAllCells() {
        if (cellWraps.isEmpty()) {
            return;
        }
        int cols = game.getCols();
        int rows = game.getRows();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                replaceCellContent(cellWraps.get(idx), c, r);
            }
        }
        updateHud();
    }

    private void refreshCell(int col, int row) {
        if (cellWraps.isEmpty()) {
            return;
        }
        int idx = row * game.getCols() + col;
        replaceCellContent(cellWraps.get(idx), col, row);
        updateHud();
    }
}
