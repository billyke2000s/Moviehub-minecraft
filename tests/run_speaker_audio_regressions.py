"""Run the actual client speaker-start logic with small world/player stubs; no renderer or VLC."""
from pathlib import Path
import subprocess
import tempfile

STUBS = {
    'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;
public record BlockPos(int x,int y,int z) { public BlockPos immutable(){return this;} }''',
    'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys; import net.minecraft.core.BlockPos;
public record Vec3(double x,double y,double z) {
 public static Vec3 atCenterOf(BlockPos p){return new Vec3(p.x()+.5,p.y()+.5,p.z()+.5);}
 public double distanceToSqr(Vec3 v){return Math.pow(x-v.x,2)+Math.pow(y-v.y,2)+Math.pow(z-v.z,2);}
}''',
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
import java.util.*; import net.minecraft.core.*; import net.minecraft.world.phys.*;
public class Minecraft {
 public Level level=new Level(); public Player player=new Player();
 public static class Level {
  public Map<BlockPos,Object> blocks=new HashMap<>();
  public boolean isLoaded(BlockPos p){return blocks.containsKey(p);}
  public Object getBlockEntity(BlockPos p){return blocks.get(p);}
 }
 public static class Player {public Vec3 eye=new Vec3(.5,.5,.5); public Vec3 getEyePosition(){return eye;}}
}''',
    'com/billyke2000s/moviehubtv/block/SpeakerBlockEntity.java': '''package com.billyke2000s.moviehubtv.block;
import net.minecraft.client.Minecraft; import net.minecraft.core.BlockPos;
public class SpeakerBlockEntity {
 public Minecraft.Level level; public BlockPos pos,target;
 public SpeakerBlockEntity(Minecraft.Level l,BlockPos p,BlockPos t){level=l;pos=p;target=t;}
 public Minecraft.Level getLevel(){return level;} public BlockPos getBlockPos(){return pos;} public BlockPos tv(){return target;}
}''',
    'com/billyke2000s/moviehubtv/block/TvBlockEntity.java': '''package com.billyke2000s.moviehubtv.block;
import java.util.*; import net.minecraft.core.BlockPos;
public class TvBlockEntity {
 public boolean on=true; public String channel="test"; public List<BlockPos> speakers=new ArrayList<>();
 public boolean isOn(){return on;} public String channelId(){return channel;} public List<BlockPos> speakers(){return speakers;}
}''',
    'com/billyke2000s/moviehubtv/block/TvScreenLayout.java': '''package com.billyke2000s.moviehubtv.block;
import java.util.*; import net.minecraft.core.*; import net.minecraft.client.Minecraft;
public class TvScreenLayout {
 public record Layout(BlockPos controller){}
 public static Map<BlockPos,BlockPos> controllers=new HashMap<>();
 public static Layout resolve(Minecraft.Level l,BlockPos p){return l.getBlockEntity(p) instanceof TvBlockEntity?new Layout(controllers.getOrDefault(p,p)):null;}
}''',
    'com/billyke2000s/moviehubtv/client/ChannelRepository.java': '''package com.billyke2000s.moviehubtv.client;
public class ChannelRepository {
 public record Channel(String id){} public static void ensureLoaded(){}
 public static Channel channel(String id){return id.isEmpty()?null:new Channel(id);}
}''',
    'com/billyke2000s/moviehubtv/client/TvVideoManager.java': '''package com.billyke2000s.moviehubtv.client;
import net.minecraft.core.BlockPos;
public class TvVideoManager {
 public static int starts; public static BlockPos last;
 public static void play(BlockPos p,ChannelRepository.Channel c){starts++;last=p;}
}''',
}

HARNESS = '''package com.billyke2000s.moviehubtv.client;
import net.minecraft.client.Minecraft; import net.minecraft.core.BlockPos; import net.minecraft.world.phys.Vec3;
import com.billyke2000s.moviehubtv.block.*;
public class SpeakerAudioTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void tick(Minecraft mc){TvVideoManager.starts=0;SpeakerAudio.tick(mc,32);}
 public static void main(String[] args){
  Minecraft mc=new Minecraft(); BlockPos speakerPos=new BlockPos(0,0,0),tvPos=new BlockPos(48,0,0);
  TvBlockEntity tv=new TvBlockEntity();tv.speakers.add(speakerPos);mc.level.blocks.put(tvPos,tv);
  SpeakerBlockEntity speaker=new SpeakerBlockEntity(mc.level,speakerPos,tvPos);mc.level.blocks.put(speakerPos,speaker);
  SpeakerAudio.loaded(speaker);tick(mc);
  check(TvVideoManager.starts==1&&TvVideoManager.last.equals(tvPos),"nearby speaker must start audio without any TV render call");
  BlockPos secondPos=new BlockPos(1,0,0); SpeakerBlockEntity second=new SpeakerBlockEntity(mc.level,secondPos,tvPos);
  mc.level.blocks.put(secondPos,second);tv.speakers.add(secondPos);SpeakerAudio.loaded(second);tick(mc);
  check(TvVideoManager.starts==1,"multiple speakers for one TV must share one player");
  tv.on=false;tick(mc);check(TvVideoManager.starts==0,"powered-off TV must not start");tv.on=true;
  mc.player.eye=new Vec3(40.5,.5,.5);tick(mc);check(TvVideoManager.starts==0,"outside speaker hearing range must not start");
  mc.player.eye=new Vec3(.5,.5,.5);speaker.target=null;second.target=null;tick(mc);
  check(TvVideoManager.starts==0,"untuned speakers stay silent");
  speaker.target=tvPos;second.target=tvPos;tv.speakers.clear();tick(mc);
  check(TvVideoManager.starts==0,"stale speaker target alone must not activate the TV");
  tv.speakers.add(speakerPos);tv.speakers.add(secondPos);
  BlockPos controller=new BlockPos(49,0,0);TvBlockEntity wall=new TvBlockEntity();
  mc.level.blocks.put(controller,wall);TvScreenLayout.controllers.put(tvPos,controller);tick(mc);
  check(TvVideoManager.starts==1&&TvVideoManager.last.equals(controller),"speaker tuned to a wall member starts its shared controller");
  mc.level.blocks.remove(speakerPos);mc.level.blocks.remove(secondPos);tick(mc);
  check(TvVideoManager.starts==0,"unloaded or broken speakers must be discarded");
  mc.level.blocks.put(speakerPos,speaker);SpeakerAudio.loaded(speaker);mc.level=new Minecraft.Level();tick(mc);
  check(TvVideoManager.starts==0,"speakers from a previous dimension must not start audio");
  SpeakerAudio.clear();
  System.out.println("PASS: actual speaker-driven audio startup, no renderer, shared wall/player, power, range and stale/unloaded links");
 }
}'''

def main():
    repo = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix='speaker-audio-') as directory:
        root = Path(directory)
        sources = dict(STUBS)
        package = 'com/billyke2000s/moviehubtv/client/'
        sources[package + 'SpeakerAudio.java'] = (repo / 'src/main/java' / package / 'SpeakerAudio.java').read_text()
        sources[package + 'SpeakerAudioTest.java'] = HARNESS
        for name, body in sources.items():
            target = root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(body)
        subprocess.run(['javac', '--release', '21', '-d', str(root / 'classes'),
                        *map(str, root.rglob('*.java'))], check=True)
        subprocess.run(['java', '-cp', str(root / 'classes'),
                        'com.billyke2000s.moviehubtv.client.SpeakerAudioTest'], check=True)

if __name__ == '__main__':
    main()
