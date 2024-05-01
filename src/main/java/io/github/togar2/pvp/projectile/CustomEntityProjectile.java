package io.github.togar2.pvp.projectile;

import io.github.togar2.pvp.entity.EntityUtils;
import io.github.togar2.pvp.events.ProjectileHitEvent.ProjectileBlockHitEvent;
import io.github.togar2.pvp.events.ProjectileHitEvent.ProjectileEntityHitEvent;
import io.github.togar2.pvp.utils.ProjectileUtils;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.projectile.ProjectileMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.entity.EntityShootEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileUncollideEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stolen from <a href="https://github.com/Minestom/Minestom/pull/496/">Pull Request #496</a> and edited
 */
public class CustomEntityProjectile extends Entity {
	private static final BoundingBox POINT_BOX = new BoundingBox(0, 0, 0);

	private final Entity shooter;
	private final @Nullable Predicate<Entity> victimsPredicate;
	private final boolean hitAnticipation;
	protected boolean noClip;
	
	protected Vec collisionDirection;
	
	private long updateCooldown = 0;
	private PhysicsResult previousPhysicsResult = null;
	
	/**
	 * Constructs new projectile.
	 *
	 * @param shooter          shooter of the projectile: may be null.
	 * @param entityType       type of the projectile.
	 * @param victimsPredicate if this projectile must not be able to hit entities, leave this null;
	 *                         otherwise it's a predicate for those entities that may be hit by that projectile.
	 */
	public CustomEntityProjectile(@Nullable Entity shooter, @NotNull EntityType entityType, @Nullable Predicate<Entity> victimsPredicate, boolean hitAnticipation) {
		super(entityType);
		this.shooter = shooter;
		this.victimsPredicate = victimsPredicate;
		this.hitAnticipation = hitAnticipation;
		setup();
	}
	
	/**
	 * Constructs new projectile that can hit living entities.
	 *
	 * @param shooter    shooter of the projectile: may be null.
	 * @param entityType type of the projectile.
	 */
	public CustomEntityProjectile(@Nullable Entity shooter, @NotNull EntityType entityType, boolean hitAnticipation) {
		this(shooter, entityType, LivingEntity.class::isInstance, hitAnticipation);
	}
	
	private void setup() {
		hasCollision = false;
		System.out.println(getAerodynamics());
		//setAerodynamics(new Aerodynamics(0.05, 0.98, 0.99));
		if (getEntityMeta() instanceof ProjectileMeta) {
			((ProjectileMeta) getEntityMeta()).setShooter(shooter);
		}
	}
	
	public @Nullable Entity getShooter() {
		return shooter;
	}
	
	/**
	 * Called when this projectile is stuck in blocks.
	 * Probably you want to do nothing with arrows in such case and to remove other types of projectiles.
	 */
	public void onStuck() {
	
	}
	
	/**
	 * Called when this projectile unstucks.
	 * Probably you want to add some random velocity to arrows in such case.
	 */
	public void onUnstuck() {
	
	}
	
	/**
	 * Called when this projectile hits an entity.
	 * Probably you want to call {@link EntityDamageEvent} in such case.
	 */
	public void onHit(Entity entity) {
	
	}
	
	public void shootFrom(Pos from, double power, double spread) {
		Point to = from.add(shooter.getPosition().direction());
		shoot(from, to, power, spread);
	}
	
	@Deprecated
	public void shootTo(Point to, double power, double spread) {
		final var from = this.shooter.getPosition().add(0D, shooter.getEyeHeight(), 0D);
		shoot(from, to, power, spread);
	}
	
	private void shoot(@NotNull Pos from, @NotNull Point to, double power, double spread) {
		// Mostly copied from Minestom PlayerProjectile
		float pitch = -from.pitch();
		double pitchDiff = pitch - 45;
		if (pitchDiff == 0) pitchDiff = 0.0001;
		double pitchAdjust = pitchDiff * 0.002145329238474369D;
		
		double dx = to.x() - from.x();
		double dy = to.y() - from.y() + pitchAdjust;
		double dz = to.z() - from.z();
		if (!hasNoGravity()) {
			final double xzLength = Math.sqrt(dx * dx + dz * dz);
			dy += xzLength * 0.20000000298023224D;
		}
		
		EntityShootEvent shootEvent = new EntityShootEvent(shooter == null ? this : shooter, this, from, power, spread);
		EventDispatcher.call(shootEvent);
		if (shootEvent.isCancelled()) {
			remove();
			return;
		}
		power = shootEvent.getPower();
		spread = shootEvent.getSpread();
		
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		dx /= length;
		dy /= length;
		dz /= length;
		Random random = ThreadLocalRandom.current();
		spread *= 0.007499999832361937D;
		dx += random.nextGaussian() * spread;
		dy += random.nextGaussian() * spread;
		dz += random.nextGaussian() * spread;
		
		final double mul = 20 * power;
		this.velocity = new Vec(dx * mul, dy * mul * 0.9, dz * mul);
		if (shooter == null) {
			setView(
					(float) Math.toDegrees(Math.atan2(dx, dz)),
					(float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))
			);
		} else {
			// Projectile view is opposite of shooting position
			setView(-from.yaw(), -from.pitch());
		}
		
		updateCooldown = System.currentTimeMillis();
	}
	
	@Override
	public void remove() {
		super.remove();
	}
	
	@Override
	public void tick(long time) {
//		if (hitAnticipation && getAliveTicks() == 0) {
//			final State state = guessNextState(getPosition());
//			handleState(state);
//			if (state != State.Flying) return;
//		}
//
//		final Pos posBefore = getPosition();
		super.tick(time);
		if (isRemoved()) return;
//		if (isRemoved()) return;
//		final Pos posNow = getPosition();
//		final State state = hitAnticipation ? guessNextState(posNow) : getState(posBefore, posNow, true);
//		handleState(state);
		
		if (isStuck() && shouldUnstuck()) {
			EventDispatcher.call(new ProjectileUncollideEvent(this));
			collisionDirection = null;
			setNoGravity(false);
			onUnstuck();
		}
	}
	
	public boolean isStuck() {
		return collisionDirection != null;
	}
	
	private boolean shouldUnstuck() {
		Vec blockPosition = this.position.asVec().apply(Vec.Operator.FLOOR);
		return isFree(blockPosition.add(collisionDirection.x(), 0, 0))
				&& isFree(blockPosition.add(0, collisionDirection.y(), 0))
				&& isFree(blockPosition.add(0, 0, collisionDirection.z()));
	}
	
	private boolean isFree(Point collidedPoint) {
		Block block = instance.getBlock(collidedPoint);
		
		// Move position slightly towards collision point because we will check for collision
		Point intersectPos = position.add(collidedPoint.sub(position).mul(0.003));
		return !block.registry().collisionShape().intersectBox(intersectPos.sub(collidedPoint), getBoundingBox());
	}
	
	@Override
	protected void movementTick() {
		// Mostly copied from Minestom
		this.gravityTickCount = isStuck() ? 0 : gravityTickCount + 1;
		if (vehicle != null) return;
		
		if (!isStuck()) {
			Vec diff = velocity.div(ServerFlag.SERVER_TICKS_PER_SECOND);
			PhysicsResult physicsResult = ProjectileUtils.simulateMovement(position, diff, POINT_BOX,
					instance.getWorldBorder(), instance, getAerodynamics(), hasNoGravity(), hasPhysics, onGround,
					false, previousPhysicsResult, true);
			this.previousPhysicsResult = physicsResult;
			
			// We won't check collisions with self for first ticks of projectile's life, because it spawns in the
			// shooter and will immediately be triggered by him.
			boolean noCollideShooter = getAliveTicks() < 6;
			PhysicsResult entityResult = CollisionUtils.checkEntityCollisions(instance, boundingBox, position,
					diff, 3, e -> {
						if (noCollideShooter && e == shooter) return false;
						return e != this;
					}, physicsResult);
			
			if (entityResult.hasCollision() && entityResult.collisionShapes()[0] instanceof Entity collided) {
				ProjectileEntityHitEvent event = new ProjectileEntityHitEvent(this, collided);
				EventDispatcher.callCancellable(event, () -> onHit(collided));
				if (isRemoved()) return;
			}
			
			Chunk finalChunk = ChunkUtils.retrieve(instance, currentChunk, physicsResult.newPosition());
			if (!ChunkUtils.isLoaded(finalChunk)) return;
			
			Pos newPosition = physicsResult.newPosition();
			if (physicsResult.hasCollision() && !isStuck()) {
				double res = EntityUtils.getResFromCollision(physicsResult.res());
				newPosition = position.add(diff.mul(res).add(diff.normalize().mul(0.01)));
				
				double signumX = physicsResult.collisionX() ? Math.signum(velocity.x()) : 0;
				double signumY = physicsResult.collisionY() ? Math.signum(velocity.y()) : 0;
				double signumZ = physicsResult.collisionZ() ? Math.signum(velocity.z()) : 0;
				Vec collisionDirection = new Vec(signumX, signumY, signumZ);
				
				Point collidedPosition = collisionDirection.add(physicsResult.newPosition()).apply(Vec.Operator.FLOOR);
				Block block = instance.getBlock(collidedPosition);
				var event = new ProjectileCollideWithBlockEvent(this, physicsResult.newPosition().withCoord(collidedPosition), block);
				EventDispatcher.callCancellable(event, () -> {
					setNoGravity(true);
					this.collisionDirection = collisionDirection;
					onStuck();
				});
			}
			
			velocity = isStuck() ? Vec.ZERO : physicsResult.newVelocity().mul(ServerFlag.SERVER_TICKS_PER_SECOND);
			onGround = physicsResult.isOnGround();
			boolean shouldSendVelocity = hasVelocity();
			
			refreshPosition(newPosition, true, false);
			if (shouldSendVelocity) sendPacketToViewers(getVelocityPacket());
			
			if (!noClip && updateCooldown + 500 < System.currentTimeMillis()) {
				float yaw = (float) Math.toDegrees(Math.atan2(diff.x(), diff.z()));
				float pitch = (float) Math.toDegrees(Math.atan2(diff.y(), Math.sqrt(diff.x() * diff.x() + diff.z() * diff.z())));
				refreshPosition(position.withView(yaw, pitch));
				updateCooldown = System.currentTimeMillis();
			}
		}
	}
	
	protected void handleState(State state) {
		if (state == State.Flying) {
			if (!noClip && hasVelocity()) {
				Vec direction = getVelocity().normalize();
				double dx = direction.x();
				double dy = direction.y();
				double dz = direction.z();
				this.position = this.position.withView(
						(float) Math.toDegrees(Math.atan2(dx, dz)),
						(float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))
				);
			}
			
			if (!super.onGround) {
				return;
			}
			super.onGround = false;
			setNoGravity(false);
			EventDispatcher.call(new ProjectileUncollideEvent(this));
			onUnstuck();
		} else if (state == State.StuckInBlock) {
			if (super.onGround) {
				return;
			}
			EventDispatcher.call(new ProjectileBlockHitEvent(this));
			super.onGround = true;
			this.velocity = Vec.ZERO;
			sendPacketToViewersAndSelf(getVelocityPacket());
			setNoGravity(true);
			onStuck();
		} else {
			Entity entity = ((State.HitEntity) state).entity;
			ProjectileEntityHitEvent event = new ProjectileEntityHitEvent(this, entity);
			EventDispatcher.callCancellable(event, () -> onHit(entity));
		}
	}
	
	protected State guessNextState(Pos posNow) {
		return getState(posNow, posNow.add(getVelocity().mul(0.06)), false);
	}
	
	/**
	 * Checks whether a projectile is stuck in block / hit an entity.
	 *
	 * @param pos    position right before current tick.
	 * @param posNow position after current tick.
	 * @return current state of the projectile.
	 */
	@SuppressWarnings("ConstantConditions")
	private State getState(Pos pos, Pos posNow, boolean shouldTeleport) {
		if (noClip) return State.Flying;
		
		if (pos.samePoint(posNow)) {
			if (instance.getBlock(posNow).isSolid()) {
				return State.StuckInBlock;
			} else {
				return State.Flying;
			}
		}
		
		Instance instance = getInstance();
		Chunk chunk = null;
		Collection<Entity> entities = null;

        /*
          What we're about to do is to discretely jump from the previous position to the new one.
          For each point we will be checking blocks and entities we're in.
         */
		double part = .25D; // half of the bounding box
		final var dir = posNow.sub(pos).asVec();
		int parts = (int) Math.ceil(dir.length() / part);
		final var direction = dir.normalize().mul(part).asPosition();
		for (int i = 0; i < parts; ++i) {
			// If we're at last part, we can't just add another direction-vector, because we can exceed end point.
			if (i == parts - 1) {
				pos = posNow;
			} else {
				pos = pos.add(direction);
			}
			if (!instance.isChunkLoaded(pos)) {
				remove();
				return State.Flying;
			}
			Point blockPos = new Pos(pos.blockX(), pos.blockY(), pos.blockZ());
			if (instance.getBlock(pos).registry().collisionShape().intersectBox(pos.sub(blockPos), getBoundingBox())) {
				if (shouldTeleport) teleport(pos);
				return State.StuckInBlock;
			}
			if (victimsPredicate == null) {
				continue;
			}
			Chunk currentChunk = instance.getChunkAt(pos);
			if (currentChunk != chunk) {
				chunk = currentChunk;
				entities = instance.getChunkEntities(chunk)
						.stream()
						.filter(victimsPredicate)
						.collect(Collectors.toSet());
			}
			
			final Pos finalPos = pos;
			Stream<Entity> victims = entities.stream().filter(entity -> getBoundingBox().intersectEntity(finalPos, entity));

            /*
              We won't check collisions with self for first ticks of projectile's life, because it spawns in the
              shooter and will immediately be triggered by him.
             */
			if (getAliveTicks() < 6) {
				victims = victims.filter(entity -> entity != getShooter());
			}
			Optional<Entity> victim = victims.findAny();
			if (victim.isPresent()) {
				return new State.HitEntity(victim.get());
			}
		}
		return State.Flying;
	}
	
	protected interface State {
		State Flying = new State() {
		};
		State StuckInBlock = new State() {
		};
		
		record HitEntity(Entity entity) implements State {}
	}
}