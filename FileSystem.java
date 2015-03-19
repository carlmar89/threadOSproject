/*
FileSystem.java
Author:     Carl Martinez
Date:       3/18/2015
Description:
    Creates a file system for threadOS. Contains system call methods
    and maintains the entire file system. A file system system call
    will be called through SysLib. SysLib will call a Kernel interrupt
    which in turn will call a FileSystem method. All methods return -1
    or false upon error.
Public Methods:
	public FileSystem(int diskBlocks)
		Initialized the file system. Will seach the DISK for a previous
		iteration and load it in, otherwise will call format().
	boolean format(int files)
		Parameters:
			files: max number of files to allow on DISK
		Restarts the file system and clears DISK of all files along wtih
		all file system sub-classes.
		Returns true on success.
	FileTableEntry open(String fileName, String mode)
		Parameters:
			fileName: name of file to open/create
			mode: "r" for read only
				  "w" for write only
				  "w+" for read/write
				  "a" for append
		Loads file into the FileTable and returns the FileTableEntry.
		If file does not exist and mode is w/w+/a it will create a new
		file.
		Returns FileTableEntry of file opened.
	int read(FileTableEntry ftEnt, byte[] buffer)
		Parameters:
			ftEnt: FileTableEntry of the file to be read
			buffer: where data is read into
		Reads data from an open file into buffer. Reads buffer size amount
		of data or until the end of file.
		Returns bytes read.
	int write(FileTableEntry ftEnt, byte[] buffer)
		Parameters:
			ftEnt: FileTableEntry of the file to be written to
		Writes buffer's data into a file. Will append to the file if data
		to be written exceeds file length.
		Returns bytes written.
	int seek(FileTableEntry ftEnt, int offset, int whence)
		Parameters:
			ftEnt: FileTableEntry of the file to adjust seekPtr of
			offset: relative distance to adject seekPtr
			whence: 0 for SEEK_SET, start offset at beginning of file
					1 for SEEK_CUR, start offset where seekPtr currently is
					2 for SEEK_END, start offset at end of file
			Sets the seekPtr of the file to specified location determined by
			the offset and the whence setting. If seekPtr is set beyond the
			file it will be set to the beginning or end of the file.
			Returns where seekPtr is in the file.
	boolean close(FileTableEntry ftEnt)
		Parameters:
			ftEnt: FileTableEntry of the file to close
		Closes a file that is open.
		Returns true on success.
	boolean delete(String fileName)
		Parameters:
			fileName: name of file to be deleted
		Deletes a file specified by fileName.
		Returns true on success.
	int fsize(FileTableEntry ftEnt)
		Parameters:
			ftEnt: FileTableEntry of the file
		Returns the size of a specified file.
	void sync()
		Syncs superblock to disk.    
 */

import java.lang.Exception;
import java.util.*;

public class FileSystem
{
	private SuperBlock superblock = null;
	private Directory dir = null;
	private FileTable filetable = null;

	private final static int SEEK_SET = 0;
	private final static int SEEK_CUR = 1;
	private final static int SEEK_END = 2;

	public FileSystem(int diskBlocks)
	{
		superblock = new SuperBlock();
		if (superblock.formatCheck())
			format(SuperBlock.DEFAULT_INODE_BLOCKS);
		else
		{
			dir = new Directory(superblock.totalInodes);
			filetable = new FileTable(dir);
			FileTableEntry dirEnt = filetable.falloc("/", "r");
			int dirSize = fsize(dirEnt);
			if (dirSize > 0 )
			{
				byte[] dirData = new byte[dirSize];
				read(dirEnt, dirData);
				dir.bytes2directory(dirData);
			}			
		}

	}

	public synchronized boolean format(int files)
	{
		if (files <= 0)
			return false;
		Inode inode = new Inode();
		byte[] buffer = new byte[Disk.blockSize];
		//update superblock
		superblock.totalInodes = files;
		superblock.freeList = (int)Math.ceil(files / 
						(double)(Disk.blockSize / inode.iNodeSize) + 1);
		superblock.lastFreeBlock = superblock.totalBlocks - 1;
		//write superblock to disk
		superblock.sync();
		//create new directory
		dir = new Directory(files);
		//create new filetable
		if (filetable == null || !filetable.fempty())
			filetable = new FileTable(dir);
		//insert new inodes
		for (short i = 0; i < files; i++)
			inode.toDisk(i);
		//update pointers for all blocks
		for (int i = superblock.freeList; i < superblock.totalBlocks - 1; i++)
		{
			SysLib.rawread(i, buffer);
			SysLib.short2bytes((short)(i + 1), buffer, 0);
			SysLib.rawwrite(i, buffer);
		}
		SysLib.rawread(superblock.totalBlocks - 1, buffer);
		SysLib.short2bytes(SuperBlock.NULL_PTR, buffer, 0);
		SysLib.rawwrite(superblock.totalBlocks - 1, buffer);
		return true;
	}

	public synchronized FileTableEntry open(String fileName, String mode)
	{
		return filetable.falloc(fileName, mode);
	}

	public synchronized int read(FileTableEntry ftEnt, byte[] buffer)
	{
		try
		{
			if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a") 
				|| buffer.length == 0)
				return -1;

			byte[] temp = new byte[Disk.blockSize];
			byte[] indirectBlock = null;

			if (ftEnt.inode.indirect > Inode.NULL_PTR)
			{
				indirectBlock = new byte[Disk.blockSize];
				SysLib.rawread(ftEnt.inode.indirect, indirectBlock);
			}

			//number of bytes to read into buffer
			int totalBytes = Math.min(ftEnt.inode.length - ftEnt.seekPtr,
										 buffer.length);
			int bytesLeft = totalBytes;
			//get block where seekPtr is
			int relativeBlock = ftEnt.seekPtr / Disk.blockSize;
			int block = getEntBlock(ftEnt);
			int index = 0;
			int currentBytes = 0;
			int bytesRead = 0;
			//read file into buffer
			while(bytesLeft > 0)
			{
				if (block < 0)
				{
					return -1;
				}
				SysLib.rawread(block, temp);
				currentBytes = Math.min(bytesLeft, Disk.blockSize - 
								(ftEnt.seekPtr % Disk.blockSize));
				//read current block of data into buffer
				for (int i = 0; i < currentBytes; i++)
				{
					buffer[index++] = temp[i + ftEnt.seekPtr % Disk.blockSize];
				}
				bytesRead += currentBytes;
				ftEnt.seekPtr += currentBytes;
				bytesLeft -= currentBytes;
				//goto next block if more bytes to read
				if(bytesLeft > 0)
				{
					if (++relativeBlock < Inode.directSize)
					{
						block = ftEnt.inode.direct[relativeBlock];
					}
					else if (indirectBlock != null)
					{
						block = (int)SysLib.bytes2short(indirectBlock, (relativeBlock - Inode.directSize) * 2);
					}
				}
			}
			return bytesRead;

		}
		catch(Exception e)
		{
			SysLib.cerr(e.toString());
			return -1;
		}


	}
	public synchronized int write(FileTableEntry ftEnt, byte[] buffer)
	{
		try
		{
			if (ftEnt.mode.equals("r") || buffer.length == 0)
				return -1;

			byte[] temp = new byte[Disk.blockSize];
			byte[] indirectBlock = null;

			//check if file has a data block
			if (ftEnt.inode.direct[0] == -1)
			{
				ftEnt.inode.direct[0] = (short)superblock.getFreeBlock();
			}
			//number of bytes to read into buffer
			int bytesLeft = buffer.length;
			//bytes to append to file
			int extraBytes = Math.max(bytesLeft + ftEnt.seekPtr - ftEnt.inode.length, 0);
			//seekPtr relative block in file
			int relativeBlock = ftEnt.seekPtr / Disk.blockSize;
			//check if indirect block is needed
			int blocksToAlloc = (extraBytes + (ftEnt.seekPtr % Disk.blockSize)) / Disk.blockSize;

			if (blocksToAlloc + relativeBlock > Inode.directSize)
			{
				indirectBlock = new byte[Disk.blockSize];
				if (ftEnt.inode.indirect == Inode.NULL_PTR)
				{
					//if indirect not in use, allocate new block
					ftEnt.inode.indirect = (short)superblock.getFreeBlock();
					SysLib.rawread(ftEnt.inode.indirect, indirectBlock);
					for (int i = 0; i < Disk.blockSize / 2; i += 2)
					{
						SysLib.short2bytes(Inode.NULL_PTR, indirectBlock, i);
					}
				}
				else
					SysLib.rawread(ftEnt.inode.indirect, indirectBlock);
			}
			int block = getEntBlock(ftEnt);
			int nextBlock = -1;
			int index = 0;
			int currentBytes = 0;
			int bytesWritten = 0;
			//write buffer to file
			while(bytesLeft > 0)
			{
				if (block < 0)
				{
					return -1;
				}
				SysLib.rawread(block, temp);
				currentBytes = Math.min(bytesLeft, Disk.blockSize - 
								(ftEnt.seekPtr % Disk.blockSize));
				//write to current block
				for (int i = 0; i < currentBytes; i++)
				{
					temp[i + ftEnt.seekPtr % Disk.blockSize] = buffer[index++];
				}
				bytesWritten += currentBytes;
				ftEnt.seekPtr += currentBytes;
				bytesLeft -= currentBytes;
				//if more to write get next block
				if(bytesLeft > 0 || ftEnt.seekPtr % Disk.blockSize == 0)
				{
					if (++relativeBlock < Inode.directSize)
					{
						nextBlock = ftEnt.inode.direct[relativeBlock];
						if (nextBlock == Inode.NULL_PTR)
						{
							nextBlock = superblock.getFreeBlock();
							ftEnt.inode.direct[relativeBlock] = (short)nextBlock;
						}
					}
					else if (indirectBlock != null)
					{
						nextBlock = (int)SysLib.bytes2short(indirectBlock, (relativeBlock - Inode.directSize) * 2);
						if (nextBlock == Inode.NULL_PTR)
						{
							nextBlock = superblock.getFreeBlock();
							SysLib.short2bytes((short)nextBlock, indirectBlock, (relativeBlock - Inode.directSize) * 2);
						}
					}
				}
				SysLib.rawwrite(block, temp);
				block = nextBlock;
			}

			if (extraBytes > 0)
				ftEnt.inode.length = ftEnt.seekPtr;
			if (indirectBlock != null)
				SysLib.rawwrite(ftEnt.inode.indirect, indirectBlock);

			ftEnt.inode.toDisk(ftEnt.iNumber);
			return bytesWritten;
		}
		catch (Exception e)
		{
			SysLib.cerr(e.toString());
			return -1;
		}
	}

	public synchronized int seek(FileTableEntry ftEnt, int offset, int whence)
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
				default:
					return -1;
			}
			if (ftEnt.seekPtr > ftEnt.inode.length)
			{
				ftEnt.seekPtr = ftEnt.inode.length;
			}
			else if (ftEnt.seekPtr < 0)
			{
				ftEnt.seekPtr = 0;
			}
			return ftEnt.seekPtr;
		}
		catch(Exception e)
		{
			return -1;
		}
	}

	public synchronized boolean close(FileTableEntry ftEnt)
	{
		notifyAll();
		return filetable.ffree(ftEnt);
	}

	public synchronized boolean delete(String fileName)
	{
		try
		{
			//delete file from directory
			short iNumber = dir.namei(fileName);
			Inode inode = new Inode(iNumber);
			while (inode.count > 0)
			{
				wait();
			}
			if (!dir.ifree(iNumber))
				return false;
			byte[] temp = new byte[Disk.blockSize];
			if (inode.length > 0)
			{
				for (int i = 0; i < Inode.directSize; i++)
				{
					if (inode.direct[i] > Inode.NULL_PTR)
						superblock.returnBlock(inode.direct[i]);
					inode.direct[i] = Inode.NULL_PTR;
				}
				if (inode.indirect != Inode.NULL_PTR)
				{
					SysLib.rawread(inode.indirect, temp);
					int offset = 0;
					short blockNum = 0;
					for (int i = 0; i < Disk.blockSize; i += 2)
					{
						blockNum = SysLib.bytes2short(temp, i);
						if (blockNum > Inode.NULL_PTR)
						{
							superblock.returnBlock(blockNum);
						}
						else
							break;
					}
					superblock.returnBlock(inode.indirect);
				}
			}
			//clear out inode
			inode.length = 0;
			inode.count = 0;
			inode.indirect = Inode.NULL_PTR;
			inode.flag = 0;
			inode.toDisk(iNumber);
			return true;
		}
		catch(Exception e)
		{
			return false;
		}

	}

	public synchronized int fsize(FileTableEntry ftEnt)
	{
		return ftEnt.inode.length;
	}

	public synchronized void sync()
	{
		superblock.sync();
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
		return (int)SysLib.bytes2short(temp, (block - Inode.directSize) * 2);
	}
}