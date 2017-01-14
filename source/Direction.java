/*
Direction.java

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
import java.awt.Point;
import java.util.Random;

/** Cardinal direction on a grid. 
    
    Note that you can iterate through directions with the syntax:

    <pre>
    for (Direction d : Direction.values()) {
       ...
    }
    </pre>
*/
public enum Direction {
    NORTH {
        public Direction left() { return WEST; }
        public Direction right() { return EAST; }
        public Direction opposite() { return SOUTH; }
        public Point forward(Point p, int n, Point overwrite) { 
            assert p != null;
            overwrite.x = p.x;
            overwrite.y = p.y - n;
            return overwrite; 
        }
        public int toInt() { return 0; }
    },

    WEST {
        public Direction left() { return SOUTH; }
        public Direction right() { return NORTH; }
        public Direction opposite() { return EAST; }
        public Point forward(Point p, int n, Point overwrite) { 
            overwrite.x = p.x - n;
            overwrite.y = p.y;
            return overwrite;
        }
        public int toInt() { return 1; }
    },

    SOUTH {
        public Direction left() { return EAST; }
        public Direction right() { return WEST; }
        public Direction opposite() { return NORTH; }
        public Point forward(Point p, int n, Point overwrite) { 
            overwrite.x = p.x;
            overwrite.y = p.y + n;
            return overwrite;
        }
        public int toInt() { return 2; }
        public boolean isHorizontal() { return false; }
    },

    EAST {
        public Direction left() { return NORTH; }
        public Direction right() { return SOUTH; }
        public Direction opposite() { return WEST; }
        public Point forward(Point p, int n, Point overwrite) { 
            overwrite.x = p.x + n;
            overwrite.y = p.y;
            return overwrite;
        }
        public int toInt() { return 3; }
    };

    final static private Random rnd = new Random();


    /** Uniformly distributed random direction. */
    // Synchronized so that access to rnd is synchronous
    synchronized public static Direction random() {
        switch (rnd.nextInt(4)) {
        case 0: return NORTH;
        case 1: return WEST;
        case 2: return SOUTH;
        case 3: return EAST;
        default: return NORTH;
        }
    }


    /** Uniformly distributed random direction. */
    public static Direction random(Random r) {
        switch (r.nextInt(4)) {
        case 0: return NORTH;
        case 1: return WEST;
        case 2: return SOUTH;
        case 3: return EAST;
        default: return NORTH;
        }
    }

    
    /** The direction 90*n-degrees to the left. */
    public Direction left(int n) {
        if (n < 0) {
            return right(-n);
        }

        Direction d = this;
        for (n = n % 4; n > 0; --n) {
            d = d.left();
        }
        return d;
    }


    /** The direction 90*n-degrees to the right. */
    public Direction right(int n) {
        if (n < 0) {
            return left(-n);
        }

        Direction d = this;
        for (n = n % 4; n > 0; --n) {
            d = d.right();
        }
        return d;
    }


    /** The direction 90-degrees to the left. */
    public abstract Direction left();


    /** The direction 90-degrees to the right. */
    public abstract Direction right();


    /** True for East and West */
    public boolean isHorizontal() {
        return (toInt() & 1) == 1;
    }


    /** True for North and South */
    public boolean isVertical() {
        return ! isHorizontal();
    }


    /** The number of turns needed to reach direction d from this direction. */
    public int numTurns(Direction d) {
        if (d == this) {
            return 0;
        } else if (d.opposite() == this) {
            return 2;
        } else {
            return 1;
        }
    }

    
    /** Returns the larger direction from a to b, favoring the vertical. */
    static public Direction fromTo(Point a, Point b) {
        int dx = b.x - a.x;
        int dy = b.y - a.y;

        if (Math.abs(dx) >= Math.abs(dy)) {
            // Mostly off on the horizontal
            if (dx < 0) {
                return WEST;
            } else {
                return EAST;
            }
        } else if (dy < 0) {
            return NORTH;
        } else {
            return SOUTH;
        }
    }


    /** Returns the direction 180 degrees from this one.*/
    public abstract Direction opposite();


    /** The point one step forward in this direction. */
    public Point forward(Point p) { return forward(p, 1); }


    /** The point <i>n</i> steps forward in this direction. */
    public Point forward(Point p, int n) {
        return forward(p, n, new Point());
    }

    /** Computes the point <i>n</i> steps forward in this direction,
      saves it to overwrite, and then returns overwrite. */
    public abstract Point forward(Point p, int n, Point overwrite);


    /** Returns a number between 0 and 3: NORTH = 0, EAST = 1, ...  */
    public abstract int toInt();

    public static Direction fromInt(int i) {
        switch (i) {
        case 0: return NORTH;
        case 1: return WEST;
        case 2: return SOUTH;
        case 3: return EAST;
        default:
            assert false : "Illegal direction int";
            return NORTH;
        }
    }
}
