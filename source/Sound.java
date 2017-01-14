/*
Sound.java

Copyright (c) 2009-2012, Morgan McGuire
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
import java.io.*;
import javax.swing.*;
import java.util.*;
import javax.sound.sampled.*;

/**
   Example:

   <pre>
    Sound s = new Sound("Wolf-Attack1.wav");
        
    s.play();
    try {
      Thread.sleep(1000);
    } catch (Exception e) {}
        
    s.unload();
   </pre>
 */
public class Sound {
    /** Clips longer than this will not be loaded. */
    static final double MAX_CLIP_DURATION_SECONDS = 100000;

    private Clip clip;
    
    public void play() {
        if (clip == null) { return; }
            
        if (clip.isActive()) {
            clip.stop();
        }
        clip.setMicrosecondPosition(0);
        clip.start();
    }

    public void unload() {
        clip.stop();
        clip.close();
        clip = null;
    }
    
    /** Clips must be explicitly unloaded when no longer in use.*/
    public Sound(String filename) {
        this(filename, MAX_CLIP_DURATION_SECONDS);
    }
    
    public Sound(String filename, double maxDuration) {
        if (! new File(filename).exists()) {
            System.err.println("WARNING: " + filename + " does not exist.");
            return;
        }

        try {
            final AudioInputStream stream = AudioSystem.getAudioInputStream(new File(filename));
            final DataLine.Info info = new DataLine.Info(Clip.class, stream.getFormat());
            clip = (Clip) AudioSystem.getLine(info);
            clip.open(stream);

            final double duration = clip.getMicrosecondLength() / 1000000.0;
            if (duration >= maxDuration) {
                System.err.println("WARNING: " + filename + " exceeded " + 
                                   maxDuration + "s and was not loaded. "+
                                   "(duration = " + duration + "s)");
                clip.close();
                clip = null;
            }
            
        } catch (Exception e) {
            System.err.println("Error while loading " + filename);
            e.printStackTrace();
            if (clip != null) {
                clip.close();
                clip = null;
            }
        }
    }


    /** Speak text out loud on OS X */
    static public void say(String message) {
        boolean osx = System.getProperty("os.name").toUpperCase().contains("MAC OS X");
        if (osx) {
            try { Runtime.getRuntime().exec("say -v Zarvox \"" + message + "\""); } catch (Exception e) {}
        }
    }

    static public void main(String args[]) {
        Sound s = new Sound("victoryLap2.wav");
        
        s.play();
        try {
            Thread.sleep(5000);
        } catch (Exception e) {}
        
        s.unload();
    }
}
