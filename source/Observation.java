/*
Observation.java

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

/** Returned by {@link Creature#look} and {@link Creature#observeSelf}. */
public final class Observation {
    static final int NO_ID = 0;

    /** Type of object at this location. */
    public Type      type;

    /** If {@link #type} == {@link Type#CREATURE}, this is the class
        name of that creature. Otherwise, null.  This is provided only
        for debugging.  It is null unless security is disabled on the
        simulator to prevent creatures from colluding to throw
        matches. */
    public String    className;

    /**
       Class ID (versus object ID) for the kind of object observed. The
       constants for this are defined in {@link Creature}.

       This can be used to distinguish creature types, since each
       creature class is assigned a unique ID at the beginning of the
       trial.  For example, you could use this to react differently to
       FlyTraps, Apples, and unknown adversary creatures.

       @see Creature#isEnemy
       @see Creature#getClassId
     */
    public int       classId = Creature.UNINITIALIZED_CLASS_ID;

    /** If {@link #type} == {@link Type#CREATURE}, this is the ID of
      that creature.  Otherwise, 0. */
    public int       id = 0;

    /** If {@link #type} == {@link Type#CREATURE}, this is the
      direction that creature is facing, otherwise, null. */
    public Direction direction;

    /** Point that was observed.*/
    public Point     position;

    /** Value returned from {@link Creature#getGameTime()} when this
        observation was made. */
    public long      time;       

    /** Fog is an experimental feature and will never appear in a
        tournament map in 2012. */
    public boolean   inFog  = false;

    public boolean   inMud  = false;

    /** {@link Creature#UNINITIALIZED_CLASS_ID} if this is not a shrine, or
     * the classID for the creature owning the shrine */
    public int       shrineClassId = Creature.UNINITIALIZED_CLASS_ID;

    public String toString() {
        return super.toString() + "{ type = " + type + 
            "; className = " + className + 
            "; classId = " + classId + 
            "; id = " + id + 
            "; direction = " + direction + 
            "; position = " + position + 
            "; time = " + time + 
            "; inMud = " + inMud + 
            "; sharineClassId = " + shrineClassId + 
            "; inFog = " + inFog + "; }";
    }


    /** Empty observation */
    public Observation(int x, int y, long t) {
        position = new Point(x, y);
        type = Type.EMPTY;
        classId = Creature.EMPTY_CLASS_ID;
        time = t;
        inFog = false;
    }


    /** Empty observation */
    public Observation(Point p, long t) {
        position = p;
        type = Type.EMPTY;
        classId = Creature.EMPTY_CLASS_ID;
        time = t;
        inFog = false;
    }

    public Observation(Point p, long t, boolean f, boolean m, int s) {
        position = p;
        type = Type.EMPTY;
        classId = Creature.EMPTY_CLASS_ID;
        time = t;
        inFog = f;
        inMud = m;
        shrineClassId = s;
    }


    /** Cannot be used to make a creature observation.
        @param tm Time of observation */
    public Observation(Point p, Type t, int classId, long tm) {
        assert t != Type.CREATURE :
        "Must call the 5-argument constructor to make a CREATURE observation";
        position = p;
        type = t;
        time = tm;
        this.classId = classId;
        inFog = false;
    }

    /** Cannot be used to make a creature observation.
        @param tm Time of observation */
    public Observation(Point p, Type t, int classId, long tm, boolean f, boolean m, int s) {
        assert t != Type.CREATURE :
        "Must call the 5-argument constructor to make a CREATURE observation";
        position = p;
        type = t;
        time = tm;
        this.classId = classId;
        inFog = f;
        inMud = m;
        shrineClassId = s;
    }


    /** @param tm Time of observation */
    public Observation(Point p, String c, int cid, int i, Direction dir, long tm) {
        position  = p;
        type      = Type.CREATURE;
        className = c;
        classId   = cid;
        id        = i;
        direction = dir;
        time      = tm;
        inFog     = false;
    }

    /** @param tm Time of observation */
    public Observation(Point p, String c, int cid, int i, Direction dir, long tm, boolean f, boolean m, int s) {
        position  = p;
        type      = Type.CREATURE;
        className = c;
        classId   = cid;
        id        = i;
        direction = dir;
        time      = tm;
        inFog     = f;
        inMud     = m;
        shrineClassId = s;
    }


    /** The classNames are not required to match, but all other fields must. */
    public boolean equals(Object obj) {
        if (obj instanceof Observation) {
            Observation other = (Observation)obj;
            return 
                (other.type.equals(type)) &&
                (other.position.equals(position)) &&
                (other.direction == direction) &&
                (other.classId == classId) &&
                (other.id == id) &&
                (other.time == time) &&
                (other.inFog == inFog);

        } else {
            return false;
        }
    }


    public int hashCode() {
        return position.hashCode() + type.hashCode() + (int)time;
    }
}
