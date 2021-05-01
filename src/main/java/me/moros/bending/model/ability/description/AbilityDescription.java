/*
 *   Copyright 2020-2021 Moros <https://github.com/PrimordialMoros>
 *
 *    This file is part of Bending.
 *
 *   Bending is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Bending is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with Bending.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.moros.bending.model.ability.description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import me.moros.atlas.cf.checker.nullness.qual.NonNull;
import me.moros.atlas.cf.checker.nullness.qual.Nullable;
import me.moros.bending.model.Element;
import me.moros.bending.model.ability.Ability;
import me.moros.bending.model.ability.util.ActivationMethod;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * AbilityDescription is immutable and thread-safe.
 * Assume that all collections returning AbilityDescription are also immutable
 */
public class AbilityDescription {
  private final String name;
  private final Function<AbilityDescription, ? extends Ability> constructor;
  private final Element element;
  private final EnumSet<ActivationMethod> activationMethods;
  private final boolean hidden;
  private final boolean canBind;
  private final boolean harmless;
  private final boolean sourcePlant;
  private final boolean bypassCooldown;
  private final int hashcode;

  private AbilityDescription(AbilityDescriptionBuilder builder) {
    name = builder.name;
    constructor = builder.constructor;
    element = builder.element;
    activationMethods = builder.activationMethods;
    canBind = builder.canBind && !isActivatedBy(ActivationMethod.SEQUENCE);
    hidden = builder.hidden;
    harmless = builder.harmless;
    sourcePlant = builder.sourcePlant;
    bypassCooldown = builder.bypassCooldown;
    hashcode = Objects.hash(name, constructor, element, activationMethods, hidden, canBind, harmless, sourcePlant, bypassCooldown);
    createAbility(); // Init config values
  }

  public @NonNull String name() {
    return name;
  }

  public @NonNull Component displayName() {
    return Component.text(name, element.color());
  }

  public @NonNull Element element() {
    return element;
  }

  public boolean canBind() {
    return canBind;
  }

  public boolean hidden() {
    return hidden;
  }

  public boolean harmless() {
    return harmless;
  }

  public boolean sourcePlant() {
    return sourcePlant;
  }

  public boolean bypassCooldown() {
    return bypassCooldown;
  }

  public boolean isActivatedBy(@NonNull ActivationMethod method) {
    return activationMethods.contains(method);
  }

  public @NonNull Ability createAbility() {
    return constructor.apply(this);
  }

  public @NonNull String permission() {
    return "bending.ability." + name;
  }

  public @NonNull Component meta() {
    String type = "Ability";
    if (isActivatedBy(ActivationMethod.PASSIVE)) {
      type = "Passive";
    } else if (isActivatedBy(ActivationMethod.SEQUENCE)) {
      type = "Sequence";
    }
    Component details = displayName().append(Component.newline())
      .append(Component.text("Element: ", NamedTextColor.DARK_AQUA))
      .append(element().displayName().append(Component.newline()))
      .append(Component.text("Type: ", NamedTextColor.DARK_AQUA))
      .append(Component.text(type, NamedTextColor.GREEN)).append(Component.newline())
      .append(Component.text("Permission: ", NamedTextColor.DARK_AQUA))
      .append(Component.text(permission(), NamedTextColor.GREEN)).append(Component.newline()).append(Component.newline())
      .append(Component.text("Click to view info about this ability.", NamedTextColor.GRAY));

    return Component.text(name(), element().color())
      .hoverEvent(HoverEvent.showText(details))
      .clickEvent(ClickEvent.runCommand("/bending info " + name()));
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AbilityDescription desc = (AbilityDescription) obj;
    return name().equals(desc.name()) && element() == desc.element();
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  /**
   * Create a {@link AbilityDescriptionBuilder} with values matching that of this object
   * @return a preconfigured builder
   */
  public @NonNull AbilityDescriptionBuilder builder() {
    return new AbilityDescriptionBuilder(name, constructor)
      .element(element).activation(activationMethods)
      .hidden(hidden).harmless(harmless)
      .sourcePlant(sourcePlant).bypassCooldown(bypassCooldown);
  }

  public static <T extends Ability> @NonNull AbilityDescriptionBuilder builder(@NonNull String name, @NonNull Function<AbilityDescription, T> constructor) {
    return new AbilityDescriptionBuilder(name, constructor);
  }

  public static class AbilityDescriptionBuilder {
    private final String name;
    private final Function<AbilityDescription, ? extends Ability> constructor;
    private Element element;
    private EnumSet<ActivationMethod> activationMethods;
    private boolean canBind = true;
    private boolean hidden = false;
    private boolean harmless = false;
    private boolean sourcePlant = false;
    private boolean bypassCooldown = false;

    public <T extends Ability> AbilityDescriptionBuilder(@NonNull String name, @NonNull Function<@NonNull AbilityDescription, @NonNull T> constructor) {
      this.name = name;
      this.constructor = constructor;
    }

    public @NonNull AbilityDescriptionBuilder element(@NonNull Element element) {
      this.element = element;
      return this;
    }

    private AbilityDescriptionBuilder activation(@NonNull Collection<@NonNull ActivationMethod> methods) {
      activationMethods = EnumSet.copyOf(methods);
      return this;
    }

    public @NonNull AbilityDescriptionBuilder activation(@NonNull ActivationMethod method, @Nullable ActivationMethod @NonNull ... methods) {
      Collection<ActivationMethod> c = new ArrayList<>();
      if (methods != null) {
        c.addAll(List.of(methods));
      }
      c.add(method);
      return activation(c);
    }

    public @NonNull AbilityDescriptionBuilder canBind(boolean canBind) {
      this.canBind = canBind;
      return this;
    }

    public @NonNull AbilityDescriptionBuilder hidden(boolean hidden) {
      this.hidden = hidden;
      return this;
    }

    public @NonNull AbilityDescriptionBuilder harmless(boolean harmless) {
      this.harmless = harmless;
      return this;
    }

    public @NonNull AbilityDescriptionBuilder sourcePlant(boolean sourcePlant) {
      this.sourcePlant = sourcePlant;
      return this;
    }

    public @NonNull AbilityDescriptionBuilder bypassCooldown(boolean bypassCooldown) {
      this.bypassCooldown = bypassCooldown;
      return this;
    }

    public @NonNull AbilityDescription build() {
      validate();
      return new AbilityDescription(this);
    }

    private void validate() {
      Objects.requireNonNull(element, "Element cannot be null");
      Objects.requireNonNull(activationMethods, "Activation Methods cannot be null");
      if (activationMethods.isEmpty()) {
        throw new IllegalStateException("Activation methods cannot be empty");
      }
    }
  }
}
