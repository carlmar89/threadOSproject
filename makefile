all: Kernel FileSystem SuperBlock Directory FileTable FileTableEntry Inode SysLib TCB

Kernel:
	javac Kernel.java

FileSystem:
	javac FileSystem.java

SuperBlock:
	javac SuperBlock.java

Directory:
	javac Directory.java

FileTable:
	javac FileTable.java

FileTableEntry:
	javac FileTableEntry.java

Inode:
	javac Inode.java

SysLib:
	javac SysLib.java

TCB:
	javac TCB.java

edit:
	subl makefile

clean:
	rm -f Kernel.class
	rm -f FileSystem.class
	rm -f SuperBlock.class
	rm -f Directory.class
	rm -f FileTable.class
	rm -f FileTableEntry.class
	rm -f Inode.class
	rm -f SysLib.class
	rm -f TCB.class
