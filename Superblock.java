import java.lang.Exception;
import java.util.*;

public class Superblock
{
	private final static int DEFAULT_INODE_BLOCKS = 64;
	public int totalBlocks; // the number of disk blocks
	public int totalInodes; // the number of inodes
	public int freeList;    // the block number of the free list's head

	public Superblock(int diskSize)
	{
		byte[] superBlock = new byte[Disk.blockSize];
		SysLib.rawread(0, superBlock);
		totalBlocks = SysLib.bytes2int(superBlock, 0);
		totalInodes = SysLib.bytes2int(superBlock, 4);
		freeList = SysLib.bytes2int(superBlock, 8);

		if (totalBlock == diskSize && totalInodes > 0 && freeList >= 2)
		{
			//valid disk contents
			return;
		}
		else
		{
			totalBlock = diskSize;
			format(DEFAULT_INODE_BLOCKS);
		}
	}
	public sync()
	{
		//write back totalBlocksm inodeBlock and free List to disk
	}
	public getFreeBlock()
	{
		//dequeue the top block from the free list
	}
	public returnBlock(int blockNumber)
	{
		//enqueue a given block to the end of the free list
	}

}