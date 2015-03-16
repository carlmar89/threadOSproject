import java.lang.Exception;
import java.util.*;

public class FileSystem
{
	private SuperBlock superblock;
	private Directory dir;
	private FileTable filetable;

	private final static int SEEK_SET = 0;
	private final static int SEEK_CUR = 1;
	private final static int SEEK_END = 2;

	public FileSystem(int diskBlocks)
	{
		superblock = new SuperBlock(diskBlocks);
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
			superblock.lastFreeBlock = superblock.totalBlocks - 1;
			//write superblock to disk
			SysLib.int2bytes(superblock.totalBlocks, 0);
			SysLib.int2bytes(superblock.totalInodes, 4);
			SysLib.int2bytes(superblock.freeList, 8);
			SysLib.int2bytes(superblock.lastFreeBlock, 12);
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
			return 0;
		}
		catch(Exception e)
		{
			return -1;
		}
	}

	public int open(String fileName, String mode)
	{
		return filetable.falloc(fileName, mode);
	}

	public int read(FileTableEntry ftEnt, byte[] buffer)
	{
		try
		{
			if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a"))
				return -1;

			byte[] temp = new byte[Disk.blockSize];

			//number of bytes to read into buffer
			int totalBytes = Math.min(ftEnt.inode.length - ftEnt.seekPtr, buffer.length);
			int bytesLeft = totalBytes;
			//get block where seekPtr is
			int block = getEntBlock(ftEnt);
			int index = 0;
			int currentBytes = 0;
			//read file into buffer
			while(bytesLeft > 0)
			{
				temp = SysLib.rawread(block, temp);
				currentBytes = Math.min(bytesLeft, Disk.blockSize - (ftEnt.seekPtr % Disk.blockSize));
				//read current block of data into buffer
				for (int i = ftEnt.seekPtr % Disk.blockSize; i < currentBytes; i++)
				{
					buffer[index++] = temp[i];
				}
				ftEnt.seekPtr += currentBytes;
				bytesLeft -= currentBytes;
				//goto next block if more bytes to read
				if(bytesLeft > 0)
				{
					block = (int)SysLib.byte2short(temp, 0);
					ftEnt.seekPtr += 2;	//skip over pointer
				}
			}
			return totalBytes;
		}
		catch(Exception e)
		{
			return -1;
		}

	}

	public int write(FileTableEntry ftEnt, byte[] buffer)
	{
		try
		{
			if (ftEnt.mode.equals("r"))
				return -1;
			else if (ftEnt.mode.equals("a"))
				ftEnt.seekPtr = ftEnt.inode.length;

			byte[] temp = new byte[Disk.blockSize];

			//number of bytes to read into buffer
			int totalBytes = buffer.length;
			int bytesLeft = totalBytes;
			//get block where seekPtr is
			int block = getEntBlock(ftEnt);
			int nextBlock = -1;
			int index = 0;
			int currentBytes = 0;
			//read buffer to file
			while(bytesLeft > 0)
			{
				temp = SysLib.rawread(block, temp);
				currentBytes = Math.min(bytesLeft, Disk.blockSize - (ftEnt.seekPtr % Disk.blockSize));
				//write to current block
				for (int i = ftEnt.seekPtr % Disk.blockSize; i < currentBytes; i++)
				{
					temp[i] = buffer[index++];
				}
				ftEnt.seekPtr += currentBytes;
				bytesLeft -= currentBytes;
				//if more to write get next block
				if(bytesLeft > 0)
				{
					nextBlock = (int)SysLib.byte2short(temp, 0);
					if (nextBlock < 0)
					{
						//get new block for file
						nextBlock = superblock.getFreeBlock();
						if (nextBlock < 0)
						{
							//no free blocks
							totalBytes -= bytesLeft;
							bytesLeft = 0;
						}
						else
						{
							//update current block pointer
							SysLib.short2bytes((short)nextBlock, temp, 0)
							ftEnt.seekPtr += 2;	//skip over pointer
						}
					}
					else
						ftEnt.seekPtr += 2;	//skip over pointer
				}
				SysLib.rawwrite(block, temp);
				block = nextBlock;
			}	
		}
		catch (Exception e)
		{
			return -1;
		}
	}

	public int seek(FileTableEntry ftEnt, int offset, int whence)
	{
		try
		{
			switch(whence)
			{
				case SEEK_SET:
					ftEnt.seekPtr = offset;
					break;
				case SEEK_CUR:
					ftEnt.seekPtr += offset;
					break;
				case SEEK_END:
					ftEnt.seekPtr = ftEnt.inode.length + offset;
					break;
			}
			if (ftEnt.seekPtr > ftEnt.inode.length)
			{
				ftEnt.seekPtr = ftEnt.inode.length;
			}
			else if (ftEnt.seekPtr < 0)
			{
				ftEnt.seekPtr = 0;
			}
			return 0;
		}
		catch(Exception e)
		{
			return -1;
		}

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

	private int getEntBlock(FileTableEntry ftEnt)
	{
		//get relative block number
		int block = ftEnt.seekPtr / Disk.blockSize;
		//get actual block number
		if (block < Inode.directSize)
		{
			return (int)ftEnt.inode.direct[block];
		}

		byte[] temp = new byte[Disk.blockSize];
		SysLib.rawread((int)ftEnt.inode.indirect, temp);
		return block = (int)SysLib.byte2short(temp, (block - Inode.directSize) * 2);
	}
}