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
			//create new directory
			dir = new Directory(files);
			//create new filetable
			if (!filetable.fempty())
				filetable = new FileTable(dir);
			//insert new inodes
			for (int i = 0; i < files; i++)
				inode.toDisk(i);
			//update pointers for all blocks
			for (int i = superblock.freeList; i < superblock.totalBlocks; i++)
			{
				SysLib.rawread(i, buffer);
				SysLib.int2bytes(i + 1, buffer, 0);
				SysLib.rawwrite(i, buffer);
			}
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