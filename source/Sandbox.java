/*
Sandbox.java

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
import java.net.*;
import java.io.*;
import java.security.*;
import java.util.HashMap;
import java.util.HashSet;

/** Loads classes into a private sandbox. Useful for making plugins
    to your Java applications, for reloading classes dynamically,
    and for loading untrusted code.

    <p>Each Sandbox loads all
    classes fresh (except those specificially designated as
    shared). If you create two Sandboxes and each loads the same
    classes, those classes will not be ==, and their instances will
    not be castable.  This allows you to reload classes at runtime, to
    run untrusted classes with separate loaders and protection
    mechanisms, and to wipe the static data of a dynamically loaded
    class.
    
    <p>In order to allow Sandboxes to communicate, all java.*
    classes are shared with the system ClassLoader and you may
    designate specific classes to be shared and not reloaded.
    
    <p>
    @see MaximumSecurityManager

    <p>Morgan McGuire
    <br>morgan@cs.williams.edu
    <br>http://graphics.cs.williams.edu
    <br>Revised April 11, 2008
*/
public class Sandbox extends URLClassLoader {
    
    /** Names and classes that should be shared; all other
        classes (except for java.* classes) will be loaded fresh. */
    final protected HashMap<String, Class> shared  = new HashMap<String, Class>();

    /** Classes that may not be loaded under any circumstances. */
    final protected HashSet<String>        prohibited = new HashSet<String>();
    
    private boolean isSecure = true;

    /** <b>Replaces</b> the system classpath with this one. */
    private Sandbox(URL[] classpath, Class[] share, boolean isSecure) {
        super(classpath, null);

        this.isSecure = isSecure;

        for (Class c : share) {
            addSharedClass(c);
        }

        // Would allow thread creation, priority setting, and access
        prohibited.add("java.lang.Thread");

        // Implicitly create threads
        prohibited.add("java.util.Timer");
        prohibited.add("javax.swing.Timer");

        // Allows "invokeLater"
        prohibited.add("javax.swing.SwingUtilities"); 

        // Allow subverting the classloader
        prohibited.add("java.lang.ClassLoader"); 
        prohibited.add("java.lang.SecureClassLoader"); 
        prohibited.add("java.lang.URLClassLoader"); 
        prohibited.add("javax.management.loading.MLet"); 

        // Allows exec and halt
        prohibited.add("java.lang.Runtime"); 
    }

    /** Prepends this directory onto the system classpath */
    private Sandbox(String addToClasspath, Class[] share, boolean isSecure) throws IOException {
        this(toClasspath(addToClasspath), share, isSecure);
    }

    /** Prepends this directory onto the system classpath */
    private Sandbox(String addToClasspath, boolean isSecure) throws IOException {
        this(toClasspath(addToClasspath), new Class[0], isSecure);
    }
        
    /** Uses default classpath. */
    private Sandbox(Class[] share, boolean isSecure) throws IOException {
        this(getSystemClasspath(), share, isSecure);

    }

    /** Uses default classpath. */
    private Sandbox(boolean isSecure) throws IOException {
        this(getSystemClasspath(), new Class[0], isSecure);

    }

    /** Share class c with this sandbox. */
    public void addSharedClass(Class c) {
        if ((c.getClassLoader() != this) && 
            (findLoadedClass(c.getName()) != null)) {
            throw new IllegalArgumentException
                ("Class " + c.getName() + 
                 " has already been loaded by " + this + " and cannot be shared.");
        }

        shared.put(c.getName(), c);
    }
    
    /** Directory is treated relative to the current dir */
    @SuppressWarnings("deprecation")
    private static URL[] toClasspath(String directory) throws IOException {
        try {
            URL url = new File(directory).toURL();
            return toClasspath(url);
        } catch (MalformedURLException e) {
            throw new IOException(e.getMessage());
        }
    }
    
    /** Concatenates two arrays */
    private static URL[] append(URL[] a, URL[] b) {
        URL[] c = new URL[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
    
    private static URL[] getSystemClasspath() {
        ClassLoader sys = ClassLoader.getSystemClassLoader();
        if (sys instanceof URLClassLoader) {
            return ((URLClassLoader)sys).getURLs();
        } else {
            return new URL[0];
        }
    }
    
    private static URL[] toClasspath(URL directory) {
        // Custom classpath
        return append(new URL[]{directory}, getSystemClasspath());
    }
    
    public Class loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    protected Class loadClass(String name, boolean b) throws ClassNotFoundException {
        final Class c = shared.get(name);

        if (! isSecure) {
            if (c != null) {
                // Use the existing shared class
                return c;
            } else {
                // Load normally
                return Class.forName(name, b, ClassLoader.getSystemClassLoader());
            }
        }

        // No need to prohibit access to the following, the
        // security manager restricts it: 
        // name.startsWith("java.lang.reflect.") ||

        // Have to prohibit access to the file system, which the
        // security manager does not restrict. (Image routines allow
        // reading and writing to disk directly).  We do allow access
        // to general Input and Output streams so that classes can
        // serialize data.  We prohibit access to File, which is safe
        // in itself, but is needed to create a java.util.Scanner.
        if (prohibited.contains(name) || 
            name.startsWith("javax.management.") ||
            name.startsWith("java.io.FileInputStream") ||
            name.startsWith("java.io.FileOutputStream") ||
            name.startsWith("java.io.FileReader") ||
            name.startsWith("java.io.FileWriter") ||
            name.startsWith("java.io.File") ||
            name.startsWith("java.nio.") ||
            name.startsWith("java.awt.imageio.")) {
            System.out.println("Accessing " + name + " is prohibited.");
            return null;
        } else if (c != null) {
            // Use the existing shared class
            return c;
        } else if (name.startsWith("java.")) {
            //System.out.println("Loading " + name);
            // Load this system class
            return Class.forName(name, b, ClassLoader.getSystemClassLoader());
        } else {
            // Load it myself
            return super.loadClass(name, b);
        }
    }

    /** Loads the class named name in its own sandbox.  The same as:
        <code>Class.forName(name, false, new Sandbox(shared));</code>
        This enables reloading of a class without terminating the
        program.

        isSecure: if true, security is enabled and the class is
        restricted from accessing illegal areas.
    */
    static Class loadIsolated(String name, Class[] shared, boolean isSecure) throws ClassNotFoundException, IOException {
        return new Sandbox(shared, isSecure).loadClass(name);
    }
}
