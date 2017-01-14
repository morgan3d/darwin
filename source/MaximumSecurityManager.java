/*
MaximumSecurityManager.java

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
import java.util.*;
import java.security.*;

/**
   A SecurityManager that prohibits all contexts except for 
   the current one from using any runtime activities except
   those specifically allowed.  By default, all other contexts
   will execute as if they were Applets (i.e., unable
   to touch the file system, reflection, etc.
*/
public class MaximumSecurityManager extends SecurityManager {
        
    /** Set of all permitted runtime actions (by default, all others are blocked.)
        See http://java.sun.com/j2se/1.4.2/docs/api/java/lang/RuntimePermission.html for the 
        list of runtime permissions. */
    final private HashSet<String> runtimePermissions = new HashSet<String>();

    /** The context that created this security manager,
        which will remain unrestricted.
     */
    private AccessControlContext whitelistContext;

    public MaximumSecurityManager() {
        whitelistContext = AccessController.getContext();
    }
    
    public MaximumSecurityManager(String[] permissions) {
        this();
        runtimePermissions.addAll(Arrays.asList(permissions));
    }

    public void checkPermission(Permission perm) {
        if (! getSecurityContext().equals(whitelistContext) &&
            (perm instanceof RuntimePermission) && 
            ! runtimePermissions.contains(perm.getName())) {

            throw new SecurityException("Not allowed to " + perm.getName());
        }
    }
}
