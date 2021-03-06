package io.anuke.mindustry.world.blocks.distribution;

import io.anuke.arc.util.*;
import io.anuke.mindustry.entities.type.TileEntity;
import io.anuke.mindustry.type.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.meta.BlockGroup;

import static io.anuke.mindustry.Vars.content;

public class Junction extends Block{
    protected float speed = 26; //frames taken to go through this junction
    protected int capacity = 6;

    public Junction(String name){
        super(name);
        update = true;
        solid = true;
        instantTransfer = true;
        group = BlockGroup.transportation;
    }

    @Override
    public boolean outputsItems(){
        return true;
    }

    @Override
    public void update(Tile tile){
        JunctionEntity entity = tile.entity();

        for(int i = 0; i < 4; i++){
            Buffer buffer = entity.buffers[i];

            if(buffer.index > 0){
                if(buffer.index > buffer.items.length) buffer.index = buffer.items.length;
                long l = buffer.items[0];
                float time = Float.intBitsToFloat(Pack.leftInt(l));

                if(Time.time() >= time + speed || Time.time() < time){

                    Item item = content.item(Pack.rightInt(l));
                    Tile dest = tile.getNearby(i);

                    //skip blocks that don't want the item, keep waiting until they do
                    if(dest == null || !dest.block().acceptItem(item, dest, tile)){
                        continue;
                    }

                    dest.block().handleItem(item, dest, tile);
                    System.arraycopy(buffer.items, 1, buffer.items, 0, buffer.index - 1);
                    buffer.index--;
                }
            }
        }
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        JunctionEntity entity = tile.entity();
        long value = Pack.longInt(Float.floatToIntBits(Time.time()), item.id);
        int relative = source.relativeTo(tile.x, tile.y);
        entity.buffers[relative].add(value);
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        JunctionEntity entity = tile.entity();
        int relative = source.relativeTo(tile.x, tile.y);

        if(entity == null || relative == -1 || entity.buffers[relative].full())
            return false;
        Tile to = tile.getNearby(relative);
        return to != null && to.target().entity != null;
    }

    @Override
    public TileEntity newEntity(){
        return new JunctionEntity();
    }

    class JunctionEntity extends TileEntity{
        Buffer[] buffers = {new Buffer(), new Buffer(), new Buffer(), new Buffer()};
    }

    class Buffer{
        long[] items = new long[capacity];
        int index;

        void add(long id){
            if(full()) return;
            items[index++] = id;
        }

        boolean full(){
            return index >= items.length - 1;
        }
    }
}
