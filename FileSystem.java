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
		superblock.freeList = (int)Math.ceil(files / (double)(Disk.blockSize / inode.iNodeSize) + 1);
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
		if (ftEnt.mode.equals("w") || ftEnt.mode.equals("a") || ftEnt.inode.length <= 2 || buffer.length == 0)
			return -1;

		byte[] temp = new byte[Disk.blockSize];

		if (ftEnt.seekPtr < 2)
		{
			ftEnt.seekPtr = 2;
		}

		//number of bytes to read into buffer
		int totalBytes = Math.min(ftEnt.inode.length - ftEnt.seekPtr, buffer.length);
		int bytesLeft = totalBytes;
		//get block where seekPtr is
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
			currentBytes = Math.min(bytesLeft, Disk.blockSize - (ftEnt.seekPtr % Disk.blockSize));
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
				block = (int)SysLib.bytes2short(temp, 0);
				ftEnt.seekPtr += 2;	//skip over pointer
			}
		}
		return bytesRead;
		/*
		try
		{

		}
		catch(Exception e)
		{
			SysLib.cerr(e.toString());
			return -1;
		}
		*/

	}
	public synchronized int write(FileTableEntry ftEnt, byte[] buffer)
	{
		try
		{
			if (ftEnt.mode.equals("r") || buffer.length == 0)
				return -1;

			byte[] temp = new byte[Disk.blockSize];
			byte[] indirectBlock = null;

			//number of bytes to read into buffer
			int bytesLeft = buffer.length;
			int extraBytes = bytesLeft + ftEnt.seekPtr - ftEnt.inode.length;
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
			int seekStart = ftEnt.seekPtr;
			//write buffer to file
			while(bytesLeft > 0)
			{
				SysLib.rawread(block, temp);
				currentBytes = Math.min(bytesLeft, Disk.blockSize - (ftEnt.seekPtr % Disk.blockSize));
				//write to current block
				for (int i = 0; i < currentBytes; i++)
				{
					temp[i + ftEnt.seekPtr % Disk.blockSize] = buffer[index++];
				}
				bytesWritten += currentBytes;
				ftEnt.seekPtr += currentBytes;
				bytesLeft -= currentBytes;
				//if more to write get next block
				if(bytesLeft > 0)
				{
					nextBlock = (int)SysLib.bytes2short(temp, 0);
					if (nextBlock < 0)
					{
						//get new block for file
						nextBlock = superblock.getFreeBlock();
						if (nextBlock < 0)
						{
							//no free blocks
							extraBytes -= bytesLeft;
							bytesLeft = 0;
						}
						else
						{
							//update current block pointer
							SysLib.short2bytes((short)nextBlock, temp, 0);
							ftEnt.seekPtr += 2;	//skip over pointer
							if (++relativeBlock < Inode.directSize)
							{
								ftEnt.inode.direct[relativeBlock] = (short)nextBlock;
							}
							else
							{
								if (indirectBlock != null)
									SysLib.short2bytes((short)nextBlock, indirectBlock, (relativeBlock - Inode.directSize) * 2);
							}
						}
					}
					else
						ftEnt.seekPtr += 2;	//skip over pointer
				}
				else
				{
					SysLib.short2bytes(SuperBlock.NULL_PTR, temp, 0);	//set final file block pointer to null
				}
				SysLib.rawwrite(block, temp);
				block = nextBlock;
			}

			if (ftEnt.seekPtr % Disk.blockSize == 0)
				ftEnt.seekPtr += 2;

			if (extraBytes > 0)
			{
				ftEnt.inode.length += ftEnt.seekPtr - seekStart;
				ftEnt.inode.toDisk(ftEnt.iNumber);
				if (indirectBlock != null)
				{
					SysLib.rawwrite(ftEnt.inode.indirect, indirectBlock);
				}
			}
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
			int pointers = 0;
			switch(whence)
			{
				case SEEK_SET:
					pointers = offset / Disk.blockSize + 1;
					ftEnt.seekPtr = offset + pointers * 2;
					break;
				case SEEK_CUR:
					if (offset > 0)
					{
						pointers = offset / Disk.blockSize;
						pointers += (ftEnt.seekPtr % Disk.blockSize + offset % Disk.blockSize) / Disk.blockSize;
					}
					else if (offset < 0)
					{
						pointers = offset / Disk.blockSize;
						pointers -= ((Disk.blockSize - (ftEnt.seekPtr % Disk.blockSize)) - offset % Disk.blockSize) / Disk.blockSize;
					}
					ftEnt.seekPtr += offset + pointers * 2;
					break;
				case SEEK_END:
					if (offset < 0)
					{
						pointers = offset / Disk.blockSize;
						pointers -= ((Disk.blockSize - (ftEnt.inode.length % Disk.blockSize)) - offset % Disk.blockSize) / Disk.blockSize;
						ftEnt.seekPtr = ftEnt.inode.length + offset + pointers * 2;
					}
					break;
				default:
					return -1;
			}
			if (ftEnt.seekPtr > ftEnt.inode.length)
			{
				ftEnt.seekPtr = ftEnt.inode.length;
			}
			else if (ftEnt.seekPtr < 2)
			{
				ftEnt.seekPtr = 2;
			}
			return ftEnt.seekPtr - 2 * (ftEnt.seekPtr / Disk.blockSize + 1);
		}
		catch(Exception e)
		{
			return -1;
		}
	}

	public synchronized boolean close(FileTableEntry ftEnt)
	{
		return filetable.ffree(ftEnt);
	}
	//need to wait until file is closed
	public synchronized boolean delete(String fileName)
	{
		try
		{
			//delete file from directory
			short iNumber = dir.namei(fileName);
			if (!dir.ifree(iNumber))
				return false;
			Inode inode = new Inode(iNumber);
			byte[] temp = new byte[Disk.blockSize];
			//set last free block's pointer to beginning of file blocks
			SysLib.rawread(superblock.lastFreeBlock, temp);
			SysLib.short2bytes(inode.direct[0], temp, 0);
			SysLib.rawwrite(superblock.lastFreeBlock, temp);
			//get last block in file
			short lastBlock = -1;
			if (inode.indirect != Inode.NULL_PTR)
			{
				SysLib.rawread(inode.indirect, temp);
				for(int i = 0; i < Disk.blockSize; i += 2)
				{
					if (SysLib.bytes2short(temp, i) == Inode.NULL_PTR)
					{
						lastBlock = SysLib.bytes2short(temp, i - 2);
						superblock.returnBlock(inode.indirect);
						break;
					}
				}
			}
			else
			{
				for (int i = 0; i < Inode.directSize; i++)
				{
					if (inode.direct[i] == Inode.NULL_PTR)
					{
						lastBlock = (short)(i - 1);
						break;
					}
					else
					{
						inode.direct[i] = Inode.NULL_PTR;
					}
				}
			}
			//sync superblock
			superblock.lastFreeBlock = lastBlock;
			superblock.sync();
			//clear out inode
			inode.length = 0;
			inode.count = 0;
			inode.indirect = 0;
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
			if (block == 0)
			{
				if (ftEnt.inode.direct[block] == Inode.NULL_PTR)
				{
					ftEnt.inode.direct[block] = (short)superblock.getFreeBlock();
					ftEnt.inode.length = 2;
					ftEnt.seekPtr = 2;
					ftEnt.inode.toDisk(ftEnt.iNumber);
				}
			}
			return (int)ftEnt.inode.direct[block];
		}

		byte[] temp = new byte[Disk.blockSize];
		SysLib.rawread((int)ftEnt.inode.indirect, temp);
		return (int)SysLib.bytes2short(temp, (block - Inode.directSize) * 2);
	}
}