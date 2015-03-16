import java.lang.Exception;
import java.util.*;

public class FileTable
{
	private Vector<FileTableEntry> table;		// the actual entity of this file table
	private Directory dir;		// the root directory 

	public FileTable(Directory directory)
	{
		table = new Vector<FileTableEntry>();     // instantiate a file (structure) table
		dir = directory;           // receive a reference to the Director
	}                             // from the file system

	// major public methods
	public synchronized FileTableEntry falloc(String filename, String mode)
	{
		// allocate a new file (structure) table entry for this file name
		// allocate/retrieve and register the corresponding inode using dir
		// increment this inode's count
		// immediately write back this inode to the disk
		// return a reference to this file (structure) table entry

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

	public synchronized boolean ffree(FileTableEntry entry)
	{
		// receive a file table entry reference
		// save the corresponding inode to the disk
		// free this file table entry.
		// return true if this file table entry found in my table

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
				// If the file table entry is no longer being used by any thread,
				// then we should decrease the amount of entries associated with
				// the Inode.
				inode.count--;

				if(inode.count == 0)
					inode.flag = Inode.UNUSED;

				inode.toDisk(entry.iNumber);

				table.remove(entry);
			}

			notifyAll();

			return true;
		}

		return false;
	}

	public synchronized boolean fempty()
	{
		return table.isEmpty( );  // return if table is empty 
	}                            // should be called before starting a format
}
