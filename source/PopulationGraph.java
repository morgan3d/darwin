/*
PopulationGraph.java

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
import java.awt.*;
import java.util.*;
import java.io.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Graph of population over time for each creature type.  Created
    by Simulation and shown by Darwin.*/
public class PopulationGraph extends javax.swing.JPanel {
        
    final private BufferedImage image = new BufferedImage(800, 120, BufferedImage.TYPE_INT_RGB);

    /** Current column of image being rendered to */
    private int                 currentX = -1;

    /** for image */
    final private Graphics2D    graphics;
        
    /** Classes to be rendered, in this order */
    private ArrayList<Class>    classArray = new ArrayList<Class>();

    /** Only update the graph 1/STALL_TICKS frames */
    private final static int    STALL_TICKS = 1;

    private int                 stallCount = -1;

    private Simulator           simulator;

    public PopulationGraph(Simulator simulator, Class[] classes) {
        this.simulator = simulator;
        graphics = (Graphics2D)image.getGraphics();
        setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        reset(classes);
    }


    public void reset(Class[] classes) {
        classArray.clear();

        // Extract all of the classes
        for (int i = 0; i < classes.length; ++i) {
            classArray.add(classes[i]);
        }
        classArray.add(Apple.class);

        if (simulator.getCreatureCount(Flytrap.class) > 0) {
            // Don't show flytraps unless they were on the map at the start
            classArray.add(Flytrap.class);
        }

        if (simulator.getCreatureCount(Treasure.class) > 0) {
            classArray.add(Treasure.class);
        }

        if (graphics != null) {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        }
    }


    synchronized public void tick() {
        stallCount = (stallCount + 1) % STALL_TICKS;
            
        if (stallCount != 0) {
            // Only redraw periodically
            return;
        }

        currentX = (currentX + 1) % image.getWidth();

        synchronized (simulator) {
            final int totalPopulation = getTotalPopulation();
                
            int y = 0;
            final int h = image.getHeight();
            for (Class c : classArray) {
                final Integer i = simulator.getCreatureCount(c);
                if (i != null) {
                    graphics.setColor(simulator.getCreatureColor(c));
                        
                    final int dy = h * i / totalPopulation;
                    graphics.fillRect(currentX, y, 1, dy);
                    y += dy;

                    graphics.setColor(Color.BLACK);
                    graphics.fillRect(currentX, y, 1, 1);
                    ++y;
                }
            }
        }

        repaint();
    }


    private int getTotalPopulation() {
        int t = 0;
        for (Class c : classArray) {
            t += simulator.getCreatureCount(c);
        }
        return t;
    }

    public synchronized void paint(Graphics g) {
        Rectangle bounds = getBounds();
        int cutWidth = bounds.width * (image.getWidth() - currentX) / image.getWidth();

        // Draw in two pieces, like a circular buffer.
        // First: from currentX forward
        g.drawImage(image,
                    bounds.x, bounds.y, bounds.x + cutWidth, bounds.y + bounds.height, 
                    currentX + 1, 0, image.getWidth(), image.getHeight(),
                    this);

        // Second: from 0 to currentX
        g.drawImage(image, 
                    bounds.x + cutWidth, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, 
                    0, 0, currentX, image.getHeight(),
                    this);

        // Draw boundary
        g.setColor(Color.BLACK);
        g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

        // Draw key
        int y = bounds.y + 15;
        int x = 5;
        for (Class c : classArray) {
            String s = c.getName();

            // Black outline
            g.setColor(Color.BLACK);
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dy = -1; dy <= 1; ++dy) {
                    if (dx != 0 || dy != 0) {
                        g.drawString(s, x + dx, y + dy);
                    }
                }
            }

            // Label
            g.setColor(simulator.getCreatureColor(c));
            g.drawString(s, x, y);

            y += (bounds.height - 20) / classArray.size();
        }
            
    }
}
