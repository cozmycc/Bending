/*
 * Copyright 2020-2024 Moros
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

package me.moros.bending.common.config.processor;

import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;

import me.moros.bending.api.config.attribute.Attribute;
import me.moros.bending.api.config.attribute.AttributeValue;
import me.moros.bending.api.util.Constants;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

record SimpleConfigEntry(String name, Class<?> type) implements ConfigEntry {
  private ConfigurationNode node(ConfigurationNode parent) {
    return parent.node(name);
  }

  @Override
  public void modify(ConfigurationNode parent, DoubleUnaryOperator operator, Consumer<Throwable> consumer) {
    ConfigurationNode node = node(parent);
    double baseValue = node.getDouble();
    try {
      node.set(toNative(operator.applyAsDouble(baseValue)));
    } catch (SerializationException e) {
      consumer.accept(e);
    }
  }

  @Override
  public AttributeValue asAttributeValue(ConfigurationNode parent, Attribute attribute, @Nullable DoubleUnaryOperator modifier) {
    double base = node(parent).getDouble();
    Number modifiedNumber = base;
    if (modifier != null) {
      double modified = modifier.applyAsDouble(base);
      if (Math.abs(modified - base) > Constants.EPSILON) {
        modifiedNumber = toNative(modified);
      }
    }
    return AttributeValue.of(attribute, name, base, modifiedNumber);
  }
}
