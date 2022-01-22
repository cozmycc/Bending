/*
 * Copyright 2020-2022 Moros
 *
 * This file is part of Bending.
 *
 * Bending is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Bending is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bending. If not, see <https://www.gnu.org/licenses/>.
 */

package me.moros.bending.config;

import me.moros.bending.Bending;
import org.spongepowered.configurate.CommentedConfigurationNode;

public abstract class Configurable implements Cloneable {
  protected CommentedConfigurationNode config;

  protected Configurable() {
    Bending.configManager().add(this);
    reload();
  }

  public abstract void onConfigReload();

  public void reload() {
    config = Bending.configManager().config();
    onConfigReload();
  }

  @Override
  protected Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}
