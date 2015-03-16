import java.lang.Exception;
import java.util.*;

public class FileSystem
{
	private Superblock superblock;
	private Directory dir;
	private FileTable filetable;


	public FileSystem(int diskBlocks)
	{
		superblock = new Superblock(diskBlocks);
		dir = new Directory(superblock.totalInodes);
		filetable = new FileTable(dir);

		FileTableEntry dirEnt = open("/", "r");
		int dirSize = fsize(dirEnt);
		if (dirSize > 0 )
		{
			byte[] dirData = new byte[dirSize];
			read(dirEnt, dirData);
			directory.bytes2directory(dirData);
		}
	}

	public int format(int files)
	{
		if (files <= 0)
			return 0;
		try
		{
			Inode inode = new Inode();
			byte[] buffer = new byte[Disk.blockSize];
			//update superblock
			superblock.totalInodes = files;
			superblock.freeList = (int)Math.ceil(files / (double)(Disk.blockSize / inode.iNodeSize) + 1);
			//write superblock to disk
			SysLib.int2bytes(superblock.totalBlocks, 0);
			SysLib.int2bytes(superblock.totalInodes, 4);
			SysLib.int2bytes(superblock.freeList, 8);
			SysLib.rawwrite(0, buffer);
			//create new directory
			dir = new Directory(files);
			//create new filetable
			if (!filetable.fempty())
				filetable = new FileTable(dir);
			//insert new inodes
			for (short i = 0; i < files; i++)
				inode.toDisk(i);
			//update pointers for all blocks
			for (short i = superblock.freeList; i < superblock.totalBlocks - 1; i++)
			{
				SysLib.rawread(i, buffer);
				SysLib.short2bytes(i + 1, buffer, 0);
				SysLib.rawwrite(i, buffer);
			}
			SysLib.rawread(superblock.totalBlocks - 1, buffer);
			SysLib.short2bytes(-1, buffer, 0);
			SysLib.rawwrite(superblock.totalBlocks - 1, buffer);
			return 1;
		}
		catch(Exception e)
		{
			return 0;
		}

	}

	public int open(String fileName, String mode)
	{

	}

	public int read(int fd, byte buffer[])
	{

	}

	public int write(int fd, byte buffer[])
	{

	}

	public int seek(int fd, int offset, int whence)
	{

	}

	public int close(int fd)
	{

	}

	public int delete(String fileName)
	{

	}

	public int fsize(int fd)
	{

	}
}