package io.anuke.mindustry.world.blocks.power;

import io.anuke.arc.Core;
import io.anuke.arc.graphics.Color;
import io.anuke.arc.graphics.g2d.Draw;
import io.anuke.arc.graphics.g2d.TextureRegion;
import io.anuke.arc.math.Mathf;
import io.anuke.arc.util.Time;
import io.anuke.mindustry.content.Fx;
import io.anuke.mindustry.entities.Effects;
import io.anuke.mindustry.entities.type.TileEntity;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.type.Liquid;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.consumers.ConsumeItemFilter;
import io.anuke.mindustry.world.consumers.ConsumeLiquidFilter;
import io.anuke.mindustry.world.meta.BlockStat;
import io.anuke.mindustry.world.meta.StatUnit;

import static io.anuke.mindustry.Vars.content;
import static io.anuke.mindustry.Vars.tilesize;

/**
 * Power generation block which can use items, liquids or both as input sources for power production.
 * Liquids will take priority over items.
 */
public class ItemLiquidGenerator extends PowerGenerator{

    protected float minItemEfficiency = 0.2f;
    /** The time in number of ticks during which a single item will produce power. */
    protected float itemDuration = 70f;

    protected float minLiquidEfficiency = 0.2f;
    /** Maximum liquid used per frame. */
    protected float maxLiquidGenerate = 0.4f;

    protected Effects.Effect generateEffect = Fx.generatespark;
    protected Effects.Effect explodeEffect = Fx.generatespark;
    protected Color heatColor = Color.valueOf("ff9b59");
    protected TextureRegion topRegion;
    protected boolean randomlyExplode = false;

    public ItemLiquidGenerator(boolean hasItems, boolean hasLiquids, String name){
        super(name);
        this.hasItems = hasItems;
        this.hasLiquids = hasLiquids;

        if(hasItems){
            consumes.add(new ConsumeItemFilter(item -> getItemEfficiency(item) >= minItemEfficiency)).update(false).optional(true, false);
        }

        if(hasLiquids){
            consumes.add(new ConsumeLiquidFilter(liquid -> getLiquidEfficiency(liquid) >= minLiquidEfficiency, maxLiquidGenerate)).update(false).optional(true, false);
        }
    }

    @Override
    public void load(){
        super.load();
        if(hasItems){
            topRegion = Core.atlas.find(name + "-top");
        }
    }

    @Override
    public void setStats(){
        super.setStats();

        if(hasItems){
            stats.add(BlockStat.productionTime, itemDuration / 60f, StatUnit.seconds);
        }
    }

    @Override
    public void update(Tile tile){
        ItemLiquidGeneratorEntity entity = tile.entity();

        //Note: Do not use this delta when calculating the amount of power or the power efficiency, but use it for resource consumption if necessary.
        //Power amount is delta'd by PowerGraph class already.
        float calculationDelta = entity.delta();

        if(!entity.cons.valid()){
            entity.productionEfficiency = 0.0f;
            return;
        }

        Liquid liquid = null;
        for(Liquid other : content.liquids()){
            if(hasLiquids && entity.liquids.get(other) >= 0.001f && getLiquidEfficiency(other) >= minLiquidEfficiency){
                liquid = other;
                break;
            }
        }

        entity.heat = Mathf.lerpDelta(entity.heat, entity.generateTime >= 0.001f ? 1f : 0f, 0.05f);

        //liquid takes priority over solids
        if(hasLiquids && liquid != null && entity.liquids.get(liquid) >= 0.001f){
            float baseLiquidEfficiency = getLiquidEfficiency(liquid);
            float maximumPossible = maxLiquidGenerate * calculationDelta;
            float used = Math.min(entity.liquids.get(liquid) * calculationDelta, maximumPossible);

            entity.liquids.remove(liquid, used);
            entity.productionEfficiency = baseLiquidEfficiency * used / maximumPossible;

            if(used > 0.001f && Mathf.chance(0.05 * entity.delta())){
                Effects.effect(generateEffect, tile.drawx() + Mathf.range(3f), tile.drawy() + Mathf.range(3f));
            }
        }else if(hasItems){
            // No liquids accepted or none supplied, try using items if accepted
            if(entity.generateTime <= 0f && entity.items.total() > 0){
                Effects.effect(generateEffect, tile.worldx() + Mathf.range(3f), tile.worldy() + Mathf.range(3f));
                Item item = entity.items.take();
                entity.productionEfficiency = getItemEfficiency(item);
                entity.explosiveness = item.explosiveness;
                entity.generateTime = 1f;
            }

            if(entity.generateTime > 0f){
                entity.generateTime -= Math.min(1f / itemDuration * entity.delta(), entity.generateTime);

                if(randomlyExplode && Mathf.chance(entity.delta() * 0.06 * Mathf.clamp(entity.explosiveness - 0.5f))){
                    //this block is run last so that in the event of a block destruction, no code relies on the block type
                    entity.damage(Mathf.random(11f));
                    Effects.effect(explodeEffect, tile.worldx() + Mathf.range(size * tilesize / 2f), tile.worldy() + Mathf.range(size * tilesize / 2f));
                }
            }else{
                entity.productionEfficiency = 0.0f;
            }
        }
    }

    @Override
    public void draw(Tile tile){
        super.draw(tile);

        ItemLiquidGeneratorEntity entity = tile.entity();

        if(hasItems){
            Draw.color(heatColor);
            Draw.alpha(entity.heat * 0.4f + Mathf.absin(Time.time(), 8f, 0.6f) * entity.heat);
            Draw.rect(topRegion, tile.drawx(), tile.drawy());
            Draw.reset();
        }

        if(hasLiquids){
            Draw.color(entity.liquids.current().color);
            Draw.alpha(entity.liquids.currentAmount() / liquidCapacity);
            drawLiquidCenter(tile);
            Draw.color();
        }
    }

    public void drawLiquidCenter(Tile tile){
        Draw.rect("blank", tile.drawx(), tile.drawy(), 2, 2);
    }

    protected float getItemEfficiency(Item item){
        return 0.0f;
    }

    protected float getLiquidEfficiency(Liquid liquid){
        return 0.0f;
    }

    @Override
    public TileEntity newEntity(){
        return new ItemLiquidGeneratorEntity();
    }

    public static class ItemLiquidGeneratorEntity extends GeneratorEntity{
        public float explosiveness;
        public float heat;
    }
}
