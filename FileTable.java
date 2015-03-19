//------------------------------------------------------------------------------
// File:		FileTable.java
// Author:		Terry Rogers
// Date:		3/18/2015
// Description: The FileTable maintains the structure of the FileSystem. The
//				sole purpose of the table is to contain file table entries which
//				are allocated via open and close methods named falloc and ffree,
//				respectively. The FileSystem open and close methods are
//				essentially wrappers around the FileTable methods.
//------------------------------------------------------------------------------
import java.lang.Exception;
import java.util.*;

public class FileTable
{
	private Vector<FileTableEntry> table;// the actual entity of this file table
	private Directory dir;		         // the root directory 

//------------------------------------------------------------------------------
// Default Constructor
//------------------------------------------------------------------------------
	public FileTable(Directory directory)
	{
		table = new Vector<FileTableEntry>(); // instantiate a file table
		dir = directory;           // receive a reference to the Directory
	}                              // from the file system

//------------------------------------------------------------------------------
// Allocates a new file (structure) table entry for the given filename.
// Allocate/retrieve and register the corresponding Inode, increments the
// Inode's count, immediately writes the Inode back to the disk, and returns
// a reference to this file (structure) table entry.
//------------------------------------------------------------------------------
	public synchronized FileTableEntry falloc(String filename, String mode)
	{
		short iNumber = -1;
		Inode inode = null;

		while(true)
		{
			iNumber = filename.equals("/") ? 0 : dir.namei(filename);

			if(iNumber >= 0)
			{
				inode = new Inode(iNumber);

				if(mode.equals("r"))
				{
					if(inode.flag == Inode.UNUSED || 
						inode.flag == Inode.READ)
					{
						inode.flag = Inode.READ;

						// No need to wait.
						break;
					}
					else if(inode.flag == Inode.WRITE)
					{
						try
						{
							// File is currently being written to, so we need
							// to wait to be notified when the writing is done.
							wait();
						}
						catch (InterruptedException e) {}
					}
					else if(inode.flag == Inode.DELETE)
					{
						// Can't read a file that's currently being deleted.
						return null;
					}
				}
				else if(mode.equals("w") || 
					mode.equals("w+") || 
					mode.equals("a"))
				{
					if(inode.flag == Inode.UNUSED)
					{
						inode.flag = Inode.WRITE;

						break;
					}
					else if(inode.flag == Inode.READ || 
						inode.flag == Inode.WRITE)
					{
						try
						{
							// File is currently being read or written to, so
							// we need to wait to be notified when the reading
							// or writing is done.
							wait();
						}
						catch(Exception e) {}
					}
					else if(inode.flag == Inode.DELETE)
					{
						// Can't write if the file is currently being deleted.
						return null;
					}
				}
				else
				{
					// Mode not supported.
					return null;
				}
			}
			else
			{
				if(mode.equals("r"))
				{
					// Can't read a file that doesn't exist.
					return null;
				}

				inode = new Inode();
				iNumber = dir.ialloc(filename);

				if(iNumber == -1)
				{
					// Can't create anymore files.
					return null;
				}
			}
		}

		inode.count++;
		inode.toDisk(iNumber);

		FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);

		table.addElement(entry);

		return entry;
	}

//------------------------------------------------------------------------------
// Receives a file table entry reference, saves the corresponding Inode to the
// disk, and frees this file table entry. Returns true if this file table entry
// found in the table. Fales otherwise, or if an error occurs.
//------------------------------------------------------------------------------
	public synchronized boolean ffree(FileTableEntry entry)
	{
		if(entry == null)
			return false;

		if(table.contains(entry))
		{
			// Remove a thread from the entry table.
			entry.count--;

			// If the entry table no longer has any threads associated with it,
			// then we need to decrease the inode count as well.
			if(entry.count == 0)
			{
				// If the file table entry is no longer being used by any
				// thread, then we should decrease the amount of entries
				// associated with the Inode.
				entry.inode.count--;

				if(entry.inode.count == 0)
					entry.inode.flag = Inode.UNUSED;

				entry.inode.toDisk(entry.iNumber);

				table.remove(entry);
			}

			notifyAll();

			return true;
		}

		return false;
	}

//------------------------------------------------------------------------------
// Checks to see if the FileTable is empty.
//------------------------------------------------------------------------------
	public synchronized boolean fempty()
	{
		return table.isEmpty( );  // return if table is empty 
	}                            // should be called before starting a format
}
