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
			return 1;
		}
		catch(Exception e)
		{
			return 0;
		}
	}

	public int open(String fileName, String mode)
	{
		return filetable.falloc(fileName, mode);
	}

	public int read(FileTableEntry ftEnt, byte[] buffer)
	{
		if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a"))
			return -1;

		int totalBytes, block, offset;
		byte[] temp = new byte[Disk.blockSize];

		//number of bytes to read into buffer
		totalBytes = Math.min(ftEnt.inode.length - ftEnt.seekPtr, buffer.length);
		//get relative block number
		block = ftEnt.seekPtr / Disk.blockSize;
		//get actual block number
		if (block < Inode.directSize)
		{
			block = (int)inode.direct[block];
		}
		else
		{
			//get block number from indirect pointer
			offset = (block - Inode.directSize) * 2;
			SysLib.rawread((int)inode.indirect, temp);
			block = (int)SysLib.byte2short(temp, offset);
		}
		int index = 0;
		int bytesLeft = totalBytes;
		int currentBytes = 0;
		//read file into buffer
		while(bytesLeft > 0)
		{
			temp = SysLib.rawread(block, temp);
			currentBytes = Math.min(bytesLeft, Disk.blockSize - (ftEnt.seekPtr % Disk.blockSize);
			//read current block of data into buffer
			for (int i = ftEnt.seekPtr % Disk.blockSize; i < currentBytes; i++)
			{
				buffer[index++] = temp[i];
			}
			ftEnt.seekPtr = ftEnt.seekPtr + currentBytes;
			bytesLeft = bytesLeft - currentBytes;
			//goto next block if more bytes to read
			if(bytesLeft > 0)
			{
				block = (int)SysLib.byte2short(temp, 0);
				ftEnt.seekPtr = ftEnt.seekPtr + 2;	//skip over pointer
			}
		}
		return totalBytes;
	}

	public int write(int fd, byte[] buffer)
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