import java.lang.Exception;
import java.util.*;

public class SuperBlock
{
	public final static int DEFAULT_INODE_BLOCKS = 64;
	public final static short NULL_PTR = -1;

	public int totalBlocks; // the number of disk blocks
	public int totalInodes; // the number of inodes
	public int freeList;    // the block number of the free list's head
	public int lastFreeBlock;

	public SuperBlock()
	{
		byte[] superBlock = new byte[Disk.blockSize];

		SysLib.rawread(0, superBlock);

		totalBlocks = SysLib.bytes2int(superBlock, 0);
		totalInodes = SysLib.bytes2int(superBlock, 4);
		freeList = SysLib.bytes2int(superBlock, 8);
		lastFreeBlock = SysLib.bytes2int(superBlock, 12);

		// The freeList has to be 2 or greater since the first block (index 0)
		// is the SuperBlock, and the the second block (index 1) contains
		// information about Inodes.
		if(totalBlocks != Kernel.NUM_BLOCKS || totalInodes <= 0 || 
			freeList < 2 || freeList >= totalBlocks && 
			lastFreeBlock < 2 || lastFreeBlock >= totalBlocks)
		{
			totalBlocks = Kernel.NUM_BLOCKS;
			lastFreeBlock = totalBlocks - 1;

			SysLib.format(DEFAULT_INODE_BLOCKS);
		}
	}

	public void sync()
	{
		byte[] buffer = new byte[Disk.blockSize];

		// Write the totalBlocks, totalInodes, and freeList.
		SysLib.int2bytes(totalBlocks, buffer, 0);
		SysLib.int2bytes(totalInodes, buffer, 4);
		SysLib.int2bytes(freeList, buffer, 8);
		SysLib.int2bytes(lastFreeBlock, buffer, 12);

		// Write the block back to disk.
		SysLib.rawwrite(0, buffer);
	}

	public int getFreeBlock()
	{
		// Store the current free block temporarily.
		int currentFreeBlock = freeList;

		if(currentFreeBlock != NULL_PTR)
		{
			byte[] buffer = new byte[Disk.blockSize];

			// Read the current free block into the buffer.
			SysLib.rawread(currentFreeBlock, buffer);

			// Update the pointer to the next free block.
			freeList = SysLib.bytes2short(buffer, 0);

			if(freeList == NULL_PTR)
				lastFreeBlock = NULL_PTR;

			// Update the current free block's pointer.
			SysLib.short2bytes(NULL_PTR, buffer, 0);

			// Write the current free block back to the disk.
			SysLib.rawwrite(currentFreeBlock, buffer);
		}

		return currentFreeBlock;
	}

	public void returnBlock(short blockNumber)
	{
		int firstFreeBlock = DEFAULT_INODE_BLOCKS / 
			(Disk.blockSize / Inode.iNodeSize) + 1;

		if(blockNumber >= firstFreeBlock && blockNumber < totalBlocks)
		{
			byte[] buffer = new byte[Disk.blockSize];

			// Read the current last free block into the buffer.
			SysLib.rawread(lastFreeBlock, buffer);

			// Update the current last free block's pointer.
			SysLib.short2bytes(blockNumber, buffer, 0);

			// Write the current last free block back to disk.
			SysLib.rawwrite(lastFreeBlock, buffer);

			// Update the SuperBlock's pointer.
			lastFreeBlock = blockNumber;

			// Read the new last free block into the buffer.
			SysLib.rawread(lastFreeBlock, buffer);

			// Update the new last free block's pointer.
			SysLib.short2bytes(NULL_PTR, buffer, 0);

			// Write the new last free block back to disk.
			SysLib.rawwrite(lastFreeBlock, buffer);
		}
	}
}
