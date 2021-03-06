//------------------------------------------------------------------------------
// File:		Inode.java
// Author:		Terry Rogers
// Date:		3/18/2015
// Description: The basic building blocks of files. Files can only exist if they
//				are associated with an Inode. Contains pointers to blocks that
//				house the data associated with the file.
//------------------------------------------------------------------------------

import java.lang.Exception;
import java.util.*;

public class Inode
{
	public final static short iNodeSize = 32;       // fix to 32 bytes
	public final static short directSize = 11;      // # direct pointers
	public final static short iNodesPerBlock = Disk.blockSize / iNodeSize;

	public final static short NULL_PTR = -1;
	public final static short ERROR = -1;

	public final static short UNUSED = 0;
	public final static short USED = 1;
	public final static short READ = 2;
	public final static short WRITE = 3;
	public final static short DELETE = 4;

	public int length;                             // file size in bytes
	public short count;                            // # file-table entries 
												   // pointing to this
	public short flag;                             // 0 = unused, 1 = used, ...
	public short direct[] = new short[directSize]; // direct pointers
	public short indirect;                         // a indirect pointer

//------------------------------------------------------------------------------
// Default Constructor
//------------------------------------------------------------------------------
	Inode()
	{
		length = 0;
		count = 0;
		flag = UNUSED;

		for(int i = 0; i < directSize; i++)
			direct[i] = NULL_PTR;

		indirect = NULL_PTR;
	}

//------------------------------------------------------------------------------
// Alternative Constructor that takes in an iNumber to pull the resepective
// Inode from disk.
//------------------------------------------------------------------------------
	Inode(short iNumber)
	{
		this();

		short blockNumber = getBlockNumber(iNumber);

		if(blockNumber != ERROR)
		{
			byte[] buffer = new byte[Disk.blockSize];

			// Read the whole block into the buffer.
			SysLib.rawread(blockNumber, buffer);

			// Set the offset to the starting address.
			short offset = getBlockOffset(iNumber);

			// Get the file size of this Inode.
			length = SysLib.bytes2int(buffer, offset);

			// Increase the offset past the length.
			offset += 4;

			// Get the file-table entry count of this Inode.
			count = SysLib.bytes2short(buffer, offset);

			// Increase the offset past the count.
			offset += 2;

			// Get the flag of this Inode.
			flag = SysLib.bytes2short(buffer, offset);

			// Increase the offset past the flag.
			offset += 2;

			// Get the direct pointers.
			for(short i = 0; i < directSize; i++)
			{
				// Get the ith direct pointer.
				direct[i] = SysLib.bytes2short(buffer, offset);

				// Increase the offset past the ith pointer.
				offset += 2;
			}

			// Get the indirect pointer.
			indirect = SysLib.bytes2short(buffer, offset);
		}
	}

//------------------------------------------------------------------------------
// Pushes the current Inode back to disk.
//------------------------------------------------------------------------------
	public void toDisk(short iNumber) 
	{
		short blockNumber = getBlockNumber(iNumber);

		if(blockNumber != ERROR)
		{
			byte[] buffer = new byte[Disk.blockSize];

			// Read in the existing contents of the block.
			SysLib.rawread(blockNumber, buffer);

			// Set the offset to the starting address.
			short offset = getBlockOffset(iNumber);

			// Write the length.
			SysLib.int2bytes(length, buffer, offset);

			// Increase the offset past the length.
			offset += 4;

			// Write the count.
			SysLib.short2bytes(count, buffer, offset);

			// Increase the offset past the count.
			offset += 2;

			// Write the flag.
			SysLib.short2bytes(flag, buffer, offset);

			// Increase the offset past the flag.
			offset += 2;

			// Write the direct pointers.
			for(int i = 0; i < directSize; i++)
			{
				// Write the ith direct pointer.
				SysLib.short2bytes(direct[i], buffer, offset);

				// Increase the offset past the ith pointer.
				offset += 2;
			}

			// Write the indirect pointer.
			SysLib.short2bytes(indirect, buffer, offset);

			// Write the block back to disk.
			SysLib.rawwrite(blockNumber, buffer);
		}
	}

//------------------------------------------------------------------------------
// Returns the block number of the Inode pointed to by iNumber.
//------------------------------------------------------------------------------
	public static short getBlockNumber(short iNumber)
	{
		// Offset by 1 since the SuperBlock is indexed at 0.
		short blockNumber = (short)(iNumber / iNodesPerBlock + 1);

		if(blockNumber < 0 || blockNumber >= Kernel.NUM_BLOCKS)
			return ERROR;

		return blockNumber;
	}

//------------------------------------------------------------------------------
// Returns the offset within an Inode block of the Inode pointed to by iNumber.
//------------------------------------------------------------------------------
	public static short getBlockOffset(short iNumber)
	{
		if(iNumber < 0)
			return ERROR;

		return (short)(iNumber % iNodesPerBlock * iNodeSize);
	}
}
