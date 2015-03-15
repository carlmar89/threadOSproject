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
			directory.bytse2directory(dirData);
		}
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