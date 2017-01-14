THE DARWIN GAME                                           version 2.1

This distribution contains: 

* the compiled simulator bundled into a jar file 
* several sample creatures as source files
* a few creatures provided without source in the jar file
* reference documentation in HTML for the classes needed to play Darwin
* the Player's Handbook as a PDF
* lots of sample maps, icons, and graphics

It also contains a zipfile of the source code for the simulator and
viewer.  This is for your own information and is not required for
playing Darwin.

To run a demo of Darwin with existing creatures, type:

  run -3D

To run Darwin with your creature, type:

  run -3D mz_1 MyCreature

You can change the name of the map on the command line and include
multiple creatures for ns_ maps.  Darwin will automatically compile
your creature if needed.  To explicitly compile your creature, just
type either:

  compile

or

  compile MyCreature.java


The creatures provided with the game (* some without source) are:

Parts of the Darwin world:
   - Flytrap
   - Apple
   - Treasure

Designed for mazes:
   - Tortoise*
   - Skunk*
   - BamfJG*

Designed for natural selection:
   - Rover
   - SuperRover
   - Pirate*
   - SheepABS*
   - PsyduckATS*

To see all of the provided maps, type:

   ls *.map

or

   dir *.map
