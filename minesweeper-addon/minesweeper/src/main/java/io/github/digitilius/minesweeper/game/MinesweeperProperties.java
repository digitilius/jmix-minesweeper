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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minesweeper")
public class MinesweeperProperties {

    private int width = 10;
    /**
     * When null, height defaults to {@link #width}.
     */
    private Integer height;
    private int mineProbabilityPercent = 15;

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public int getEffectiveHeight() {
        return height != null ? height : width;
    }

    public int getMineProbabilityPercent() {
        return mineProbabilityPercent;
    }

    public void setMineProbabilityPercent(int mineProbabilityPercent) {
        this.mineProbabilityPercent = mineProbabilityPercent;
    }
}
