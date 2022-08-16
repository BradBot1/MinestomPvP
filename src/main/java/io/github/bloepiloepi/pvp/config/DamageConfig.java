package io.github.bloepiloepi.pvp.config;

import io.github.bloepiloepi.pvp.listeners.DamageListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.EntityEvent;

/**
 * Creates an EventNode with damage events.
 * This includes armor, shields, damage invulnerability, and fall damage.
 * It only reduces damage based on armor attribute,
 * to change that attribute for different types of armor you need {@code ArmorToolConfig}.
 */
public class DamageConfig extends ElementConfig<EntityEvent> {
	public static final DamageConfig DEFAULT = new DamageConfigBuilder(false).defaultOptions().build();
	public static final DamageConfig LEGACY = new DamageConfigBuilder(true).legacyOptions().build();
	
	private final boolean
			fallDamageEnabled, equipmentDamageEnabled, shieldEnabled,
			legacyShieldMechanics, armorEnabled, exhaustionEnabled,
			legacyKnockback, soundsEnabled;
	private final int invulnerabilityTicks;
	
	public DamageConfig(boolean legacy, boolean fallDamageEnabled, boolean equipmentDamageEnabled,
	                    boolean shieldEnabled, boolean legacyShieldMechanics, int invulnerabilityTicks,
	                    boolean armorEnabled, boolean exhaustionEnabled, boolean legacyKnockback,
	                    boolean soundsEnabled) {
		super(legacy);
		this.fallDamageEnabled = fallDamageEnabled;
		this.equipmentDamageEnabled = equipmentDamageEnabled;
		this.shieldEnabled = shieldEnabled;
		this.legacyShieldMechanics = legacyShieldMechanics;
		this.invulnerabilityTicks = invulnerabilityTicks;
		this.armorEnabled = armorEnabled;
		this.exhaustionEnabled = exhaustionEnabled;
		this.legacyKnockback = legacyKnockback;
		this.soundsEnabled = soundsEnabled;
	}
	
	public boolean isFallDamageEnabled() {
		return fallDamageEnabled;
	}
	
	public boolean isEquipmentDamageEnabled() {
		return equipmentDamageEnabled;
	}
	
	public boolean isShieldEnabled() {
		return shieldEnabled;
	}
	
	public boolean isLegacyShieldMechanics() {
		return legacyShieldMechanics;
	}
	
	public int getInvulnerabilityTicks() {
		return invulnerabilityTicks;
	}
	
	public boolean isArmorDisabled() {
		return !armorEnabled;
	}
	
	public boolean isExhaustionEnabled() {
		return exhaustionEnabled;
	}
	
	public boolean isLegacyKnockback() {
		return legacyKnockback;
	}
	
	public boolean isSoundsEnabled() {
		return soundsEnabled;
	}
	
	@Override
	public EventNode<EntityEvent> createNode() {
		return DamageListener.events(this);
	}
	
	/**
	 * Creates an empty builder which has everything disabled.
	 *
	 * @return An empty builder
	 */
	public static DamageConfigBuilder builder(boolean legacy) {
		return new DamageConfigBuilder(legacy);
	}
}
