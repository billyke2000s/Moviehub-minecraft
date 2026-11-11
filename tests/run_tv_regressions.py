#!/usr/bin/env python3
"""Run production placement/wall code with a small fake Minecraft API (not an in-game test)."""
from pathlib import Path
import subprocess
import tempfile
import json
import os

STUBS = {
'net/minecraft/core/Direction.java': '''package net.minecraft.core; public enum Direction { NORTH,SOUTH,EAST,WEST; public Direction getOpposite(){return switch(this){case NORTH->SOUTH;case SOUTH->NORTH;case EAST->WEST;case WEST->EAST;};} }''',
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core; public record BlockPos(int x,int y,int z) { public BlockPos immutable(){return this;} public BlockPos relative(Direction d){return relative(d,1);} public BlockPos relative(Direction d,int n){return new BlockPos(x+(d==Direction.EAST?n:d==Direction.WEST?-n:0),y,z+(d==Direction.SOUTH?n:d==Direction.NORTH?-n:0));} public BlockPos above(){return above(1);}public BlockPos above(int n){return new BlockPos(x,y+n,z);}public BlockPos below(){return above(-1);} }''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player; import net.minecraft.core.Direction;public record Player(Direction direction){public Direction getDirection(){return direction;}}''',
'net/minecraft/world/item/context/BlockPlaceContext.java': '''package net.minecraft.world.item.context;import net.minecraft.core.Direction;import net.minecraft.world.entity.player.Player;public record BlockPlaceContext(Player player,Direction horizontalDirection){public Player getPlayer(){return player;}public Direction getHorizontalDirection(){return horizontalDirection;}}''',
'net/minecraft/world/level/block/Block.java': '''package net.minecraft.world.level.block;public class Block {public static final int UPDATE_ALL=3;public net.minecraft.world.level.block.state.BlockState defaultBlockState(){return new net.minecraft.world.level.block.state.BlockState(this);}}''',
'net/minecraft/world/level/block/state/BlockState.java': '''package net.minecraft.world.level.block.state;import java.util.*;import net.minecraft.world.level.block.Block;public class BlockState{public record P<T>(String name){}private final Block b;private final Map<P<?>,Object>v;public BlockState(Block b){this(b,new HashMap<>());}private BlockState(Block b,Map<P<?>,Object>v){this.b=b;this.v=v;}public Block getBlock(){return b;}public boolean is(Block b){return this.b==b;}@SuppressWarnings("unchecked")public<T>T getValue(P<T>p){return(T)v.get(p);}public<T>BlockState setValue(P<T>p,T x){var n=new HashMap<>(v);n.put(p,x);return new BlockState(b,n);}}''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;import java.util.*;import net.minecraft.core.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.*;import com.billyke2000s.moviehubtv.block.*;public class Level{public boolean isClientSide=false;public long time;public long getGameTime(){return time;}public final Map<BlockPos,BlockState>states=new HashMap<>();public final Map<BlockPos,TvBlockEntity>entities=new HashMap<>();public final Set<BlockPos>unloaded=new HashSet<>();private static final BlockState AIR=new Block().defaultBlockState();public boolean isLoaded(BlockPos p){return!unloaded.contains(p);}public BlockState getBlockState(BlockPos p){return states.getOrDefault(p,AIR);}public TvBlockEntity getBlockEntity(BlockPos p){return entities.get(p);}public boolean setBlock(BlockPos p,BlockState s,int flags){BlockState old=getBlockState(p);states.put(p,s);if(old.getBlock()!=s.getBlock()){entities.remove(p);if(s.getBlock() instanceof TvBlock)entities.put(p,new TvBlockEntity());}return true;}}''',
'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;public class ServerLevel extends net.minecraft.world.level.Level{}''',
'net/neoforged/neoforge/event/tick/ServerTickEvent.java': '''package net.neoforged.neoforge.event.tick;public class ServerTickEvent{public static class Post{}}''',
'net/neoforged/neoforge/event/server/ServerStoppedEvent.java': '''package net.neoforged.neoforge.event.server;public class ServerStoppedEvent{}''',
'com/billyke2000s/moviehubtv/MovieHubTV.java': '''package com.billyke2000s.moviehubtv;public class MovieHubTV{public static final Log LOGGER=new Log();public static class Log{public void warn(String s,Object...v){}public void info(String s,Object...v){}}}''',
'com/billyke2000s/moviehubtv/ModRegistry.java': '''package com.billyke2000s.moviehubtv;import com.billyke2000s.moviehubtv.block.*;public class ModRegistry{public static final Holder TV=new Holder();public static class Holder{final TvBlock block=new TvBlock();public TvBlock get(){return block;}}}''',
'com/billyke2000s/moviehubtv/block/TvBlock.java': '''package com.billyke2000s.moviehubtv.block;import net.minecraft.core.*;import net.minecraft.world.level.block.*;import net.minecraft.world.level.block.state.BlockState.P;public class TvBlock extends Block{public static final P<Direction>FACING=new P<>("facing");public static final P<Boolean>POWERED=new P<>("powered");public static final P<WallPart>WALL_COLUMN=new P<>("column"),WALL_ROW=new P<>("row");public enum WallPart{SINGLE,LEFT,MIDDLE,RIGHT,BOTTOM,TOP;static WallPart column(int x,int w){return w<=1?SINGLE:x==0?LEFT:x==w-1?RIGHT:MIDDLE;}static WallPart row(int y,int h){return h<=1?SINGLE:y==0?BOTTOM:y==h-1?TOP:MIDDLE;}}public static Direction rightOf(Direction d){return switch(d){case NORTH->Direction.WEST;case WEST->Direction.SOUTH;case SOUTH->Direction.EAST;case EAST->Direction.NORTH;};}@Override public net.minecraft.world.level.block.state.BlockState defaultBlockState(){return super.defaultBlockState().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(WALL_COLUMN,WallPart.SINGLE).setValue(WALL_ROW,WallPart.SINGLE);}}''',
'com/billyke2000s/moviehubtv/block/BigTvBlock.java': '''package com.billyke2000s.moviehubtv.block;import net.minecraft.core.*;import net.minecraft.world.level.block.state.BlockState.P;public class BigTvBlock extends TvBlock{public static final P<Integer>PART_X=new P<>("px"),PART_Y=new P<>("py");public int widthBlocks(){return 3;}public int heightBlocks(){return 2;}public static BlockPos partPos(BlockPos p,Direction d,int x,int y){return p.relative(rightOf(d),x).above(y);}}''',
'com/billyke2000s/moviehubtv/block/TvBlockEntity.java': '''package com.billyke2000s.moviehubtv.block;public class TvBlockEntity{public int x,y,w=1,h=1;boolean on,guide=true;String channel="";public boolean isOn(){return on;}public boolean guideOpenFlag(){return guide;}public String channelId(){return channel;}public void restore(boolean o,String c,boolean g){on=o;channel=c;guide=g;}public void setWall(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}public void refreshPower(){}}''',
}
# Minimal API additions for the production footprint validator.
STUBS['org/jetbrains/annotations/Nullable.java'] = 'package org.jetbrains.annotations;import java.lang.annotation.*;@Target({ElementType.TYPE_USE,ElementType.PARAMETER})public @interface Nullable{}'
key = 'com/billyke2000s/moviehubtv/block/TvBlockEntity.java'
STUBS[key] = STUBS[key].replace('public boolean isOn()', 'public int wallX(){return x;}public int wallY(){return y;}public int wallWidth(){return w;}public int wallHeight(){return h;}public net.minecraft.core.Direction cachedFacing=net.minecraft.core.Direction.NORTH; public boolean isOn()')
key = 'com/billyke2000s/moviehubtv/block/BigTvBlock.java'
STUBS[key] = STUBS[key].replace('public int widthBlocks()', 'public net.minecraft.core.BlockPos controllerPos(net.minecraft.world.level.block.state.BlockState s,net.minecraft.core.BlockPos p){return p.relative(rightOf(s.getValue(FACING)),-s.getValue(PART_X)).above(-s.getValue(PART_Y));} public int widthBlocks()')

HARNESS = r'''package com.billyke2000s.moviehubtv.block;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.neoforged.neoforge.event.tick.*;
import net.neoforged.neoforge.event.server.*;
import com.billyke2000s.moviehubtv.*;

public class Regression {
 static final BlockPos ORIGIN=new BlockPos(0,64,0);
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static BlockPos pos(Direction f,int x,int y){return TvWall.memberPos(ORIGIN,f,x,y);}
 static void tick(ServerLevel l){l.time+=20;BigTvConverter.onServerTick(new ServerTickEvent.Post());}
 static ServerLevel wall(Direction f,int w,int h){
  var l=new ServerLevel();for(int x=0;x<w;x++)for(int y=0;y<h;y++)l.setBlock(pos(f,x,y),ModRegistry.TV.get().defaultBlockState().setValue(TvBlock.FACING,f),3);return l;
 }
 static void joined(ServerLevel l,Direction f,int w,int h){
  for(int x=0;x<w;x++)for(int y=0;y<h;y++){
   var p=pos(f,x,y);var s=l.getBlockState(p);var e=l.entities.get(p);
   check(s.getValue(TvBlock.FACING)==f,"facing must survive joining");
   check(e.x==x&&e.y==y&&e.w==w&&e.h==h,"one shared wall layout");
   check(s.getValue(TvBlock.WALL_COLUMN)==TvBlock.WallPart.column(x,w),"joined horizontal frame");
   check(s.getValue(TvBlock.WALL_ROW)==TvBlock.WallPart.row(y,h),"joined vertical frame");
   var layout=TvScreenLayout.resolve(l,p);
   check(layout!=null&&layout.controller().equals(ORIGIN)&&layout.facing()==f&&layout.width()==w&&layout.height()==h,"physical screen footprint");
  }
 }
 public static void main(String[]args){
  for(Direction f:Direction.values())for(Direction context:Direction.values()){
   check(TvPlacement.facing(new BlockPlaceContext(new Player(f),context))==f.getOpposite(),"placing player's actual heading wins over context");
   check(TvPlacement.facing(new BlockPlaceContext(null,context))==context.getOpposite(),"non-player placement fallback");
  }
  System.out.println("PASS: actual placement helper, all four player headings and overridden-context fallback");
  for(Direction f:Direction.values())for(int[]size:new int[][]{{1,1},{2,1},{2,2},{3,2}}){
   var l=wall(f,size[0],size[1]);check(TvWall.recompute(l,ORIGIN),"regroup");joined(l,f,size[0],size[1]);
  }
  System.out.println("PASS: 1, 2, 4 and 6 TVs share the correct seamless frame in all four directions while off");
  for(int[]size:new int[][]{{1,2},{3,1},{2,3},{4,2}}){var l=wall(Direction.NORTH,size[0],size[1]);TvWall.recompute(l,ORIGIN);for(var e:l.entities.values())check(e.w==1&&e.h==1,"unsupported shapes must not become another TV size");}
  System.out.println("PASS: unsupported rectangles stay separate instead of creating additional sizes");
  var l=wall(Direction.EAST,3,2);TvWall.recompute(l,ORIGIN);l.unloaded.add(pos(Direction.EAST,2,1));BigTvConverter.queueRegroup(l,ORIGIN);tick(l);check(l.entities.get(ORIGIN).w==3,"partial load must preserve saved wall");l.unloaded.clear();tick(l);joined(l,Direction.EAST,3,2);
  l.setBlock(pos(Direction.EAST,2,1),new Block().defaultBlockState(),3);TvWall.recomputeAround(l,pos(Direction.EAST,2,1),Direction.EAST);tick(l);check(l.entities.get(ORIGIN).w==1,"incomplete group splits");l.setBlock(pos(Direction.EAST,2,1),ModRegistry.TV.get().defaultBlockState().setValue(TvBlock.FACING,Direction.EAST),3);BigTvConverter.queueRegroup(l,pos(Direction.EAST,2,1));tick(l);joined(l,Direction.EAST,3,2);
  System.out.println("PASS: delayed chunks preserve walls; removing/replacing a part splits and rejoins");
  var legacy=new ServerLevel();var big=new BigTvBlock();for(int x=0;x<3;x++)for(int y=0;y<2;y++)legacy.setBlock(pos(Direction.WEST,x,y),big.defaultBlockState().setValue(TvBlock.FACING,Direction.WEST).setValue(BigTvBlock.PART_X,x).setValue(BigTvBlock.PART_Y,y),3);legacy.entities.get(ORIGIN).restore(true,"test-channel",false);check(TvScreenLayout.resolve(legacy,pos(Direction.WEST,2,1)).controller().equals(ORIGIN),"legacy footprint controller");legacy.unloaded.add(pos(Direction.WEST,2,1));check(TvScreenLayout.resolve(legacy,ORIGIN)==null,"unloaded legacy footprint cannot render");BigTvConverter.queue(legacy,ORIGIN);tick(legacy);check(legacy.getBlockState(ORIGIN).getBlock()==big,"legacy conversion must wait");legacy.unloaded.clear();tick(legacy);joined(legacy,Direction.WEST,3,2);check(legacy.entities.get(ORIGIN).isOn()&&legacy.entities.get(ORIGIN).channelId().equals("test-channel"),"migration saved state");BigTvConverter.onServerStopped(new ServerStoppedEvent());
  System.out.println("PASS: legacy conversion retries without losing power, channel or facing");
  var stale=wall(Direction.NORTH,3,2);TvWall.recompute(stale,ORIGIN);
  stale.entities.get(ORIGIN).cachedFacing=Direction.EAST;
  check(TvScreenLayout.resolve(stale,ORIGIN).facing()==Direction.NORTH,"world casing state must override cached BE facing");
  var missing=pos(Direction.NORTH,2,1);stale.unloaded.add(missing);
  check(TvScreenLayout.resolve(stale,ORIGIN)==null,"incomplete loaded footprint cannot render");
  stale.unloaded.clear();var entity=stale.entities.get(missing);entity.w=2;
  check(TvScreenLayout.resolve(stale,ORIGIN)==null,"mismatched member dimensions cannot render");
  entity.w=3;
  stale.setBlock(ORIGIN,stale.getBlockState(ORIGIN).setValue(TvBlock.WALL_COLUMN,TvBlock.WallPart.SINGLE),3);
  check(TvScreenLayout.resolve(stale,ORIGIN)==null,"unjoined casing cannot draw a joined overlay");
  BigTvConverter.queueRegroup(stale,ORIGIN);tick(stale);joined(stale,Direction.NORTH,3,2);
  System.out.println("PASS: world-facing authority, unloaded/mismatched members and missing frames; repair rejoins the casing");
  for(var e:stale.states.entrySet())e.setValue(e.getValue().setValue(TvBlock.FACING,Direction.EAST));
  for(var p:stale.states.keySet())check(TvScreenLayout.resolve(stale,p)==null,"3x2 metadata on perpendicular physical blocks must never draw into air");
  for(var p:stale.states.keySet())BigTvConverter.queueRegroup(stale,p);tick(stale);
  for(var p:stale.states.keySet()){var layout=TvScreenLayout.resolve(stale,p);check(layout!=null&&layout.width()==1&&layout.height()==1,"repair must use actual supported geometry, not guess an orientation");}
  var invalid=stale.entities.get(ORIGIN);invalid.x=-1;check(TvScreenLayout.resolve(stale,ORIGIN)==null,"invalid wall offset");invalid.x=0;
  System.out.println("PASS: perpendicular saved layout rejected before drawing; server repair produces valid physical screens");
 }
}'''

SPEAKER_TEST = r'''package com.billyke2000s.moviehubtv.client;
public class SpeakerTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a){
  double[] none={};
  check(SpeakerMix.volume(80,100,16,0,none)==80,"default TV volume next to the TV");
  check(SpeakerMix.volume(80,100,16,8,none)==40,"fades with distance");
  check(SpeakerMix.volume(80,100,16,16,none)==0,"silent at hearing range");
  check(SpeakerMix.volume(100,100,16,0,none)==100,"TV volume up to 100");
  check(SpeakerMix.volume(0,100,16,0,new double[]{0})==0,"muted TV is silent even at a speaker");
  check(SpeakerMix.volume(80,50,16,0,none)==40,"master volume scales every TV");
  check(SpeakerMix.volume(80,100,16,40,new double[]{0})==80,"next to a speaker: as loud as next to the TV");
  check(SpeakerMix.volume(80,100,16,40,new double[]{30,8})==40,"nearest speaker counts");
  check(SpeakerMix.volume(80,100,16,40,new double[]{20,30})==0,"too far from TV and speakers: silent");
  check(SpeakerMix.volume(150,120,16,0,none)==100,"volumes clamped to 100");
  check(SpeakerMix.volume(-5,100,16,0,none)==0,"negative volume clamped to 0");
  System.out.println("PASS: TV volume, master volume and speaker hearing maths");
 }
}'''

def run_speaker_tests(repo):
    with tempfile.TemporaryDirectory(prefix='tv-speaker-') as temporary:
        root=Path(temporary)
        package=root/'com/billyke2000s/moviehubtv/client'; package.mkdir(parents=True)
        (package/'SpeakerMix.java').write_text((repo/'src/main/java/com/billyke2000s/moviehubtv/client/SpeakerMix.java').read_text())
        (package/'SpeakerTest.java').write_text(SPEAKER_TEST)
        subprocess.run(['javac','--release','21','-d',str(root/'classes'),*[str(p) for p in root.rglob('*.java')]],check=True)
        subprocess.run(['java','-cp',str(root/'classes'),'com.billyke2000s.moviehubtv.client.SpeakerTest'],check=True)
    # Speaker resources exist and the recipe uses only vanilla items.
    resources=repo/'src/main/resources'
    recipe=json.loads((resources/'data/moviehubtv/recipe/speaker.json').read_text())
    assert recipe['result']['id']=='moviehubtv:speaker' and recipe['pattern']==['PWP','PNP','PRP']
    for key in ['assets/moviehubtv/blockstates/speaker.json','assets/moviehubtv/models/block/speaker.json',
                'assets/moviehubtv/models/item/speaker.json','assets/moviehubtv/textures/block/speaker_side.png',
                'assets/moviehubtv/textures/block/speaker_top.png','data/moviehubtv/loot_table/blocks/speaker.json']:
        assert (resources/key).exists(), key
    assert 'moviehubtv:speaker' in json.loads((resources/'data/minecraft/tags/block/mineable/axe.json').read_text())['values']
    assert 'block.moviehubtv.speaker' in json.loads((resources/'assets/moviehubtv/lang/en_us.json').read_text())
    assert 'block.moviehubtv.speaker.linked' in json.loads((resources/'assets/moviehubtv/lang/en_us.json').read_text())
    code=(repo/'src/main/java/com/billyke2000s/moviehubtv')
    tv=(code/'block/TvBlockEntity.java').read_text()
    assert 'ACTION_VOLUME_UP' in tv and 'putInt("Volume"' in tv and 'putLongArray("Speakers"' in tv
    assert 'SpeakerLink.link' in (code/'block/SpeakerBlock.java').read_text()
    assert 'ACTION_VOLUME_UP' in (code/'client/RemoteControl.java').read_text()
    assert 'SpeakerBlock.tune(stack' in (code/'block/TvBlock.java').read_text()
    assert 'SpeakerLink.linkTuned' in (code/'block/SpeakerBlock.java').read_text()
    assert 'block.moviehubtv.speaker.tuned' in json.loads((resources/'assets/moviehubtv/lang/en_us.json').read_text())
    print('PASS: speaker block resources, recipe, loot, tool tag; volume and speaker links wired in')

def main():
    repo=Path(__file__).resolve().parents[1]
    run_speaker_tests(repo)
    with tempfile.TemporaryDirectory(prefix='tv-regression-') as temporary:
        root=Path(temporary)
        sources=dict(STUBS)
        sources['com/billyke2000s/moviehubtv/block/Regression.java']=HARNESS
        for name in ['TvPlacement.java','TvWall.java','BigTvConverter.java','TvScreenLayout.java']:
            relative='com/billyke2000s/moviehubtv/block/'+name
            sources[relative]=(repo/'src/main/java'/relative).read_text()
        for name,body in sources.items():
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(body)
        subprocess.run(['javac','--release','21','-d',str(root/'classes'),*[str(p) for p in root.rglob('*.java')]],check=True)
        subprocess.run(['java','-cp',str(root/'classes'),'com.billyke2000s.moviehubtv.block.Regression'],check=True)
    # Check the actual block classes use the tested helper (the test fixture does not instantiate them).
    for name in ['TvBlock.java','BigTvBlock.java']:
        text=(repo/'src/main/java/com/billyke2000s/moviehubtv/block'/name).read_text()
        assert 'TvPlacement.facing(context)' in text, name
    assets=repo/'src/main/resources/assets/moviehubtv'
    variants=json.loads((assets/'blockstates/tv.json').read_text())['variants']
    assert len(variants)==128
    for key,value in variants.items():
        facing=key.split(',')[0].split('=')[1]
        assert value.get('y',0)=={'north':0,'east':90,'south':180,'west':270}[facing]
        alternate=key.replace('powered=false','powered=true')
        assert value==variants[alternate], 'on/off must use the same joined frame'
        assert (assets/'models/block'/(value['model'].split('/')[-1]+'.json')).exists()
    print('PASS: block delegation and all 128 model variants; identical joined frame on/off')

if __name__=='__main__': main()
