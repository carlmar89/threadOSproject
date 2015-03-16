import java.lang.Exception;
import java.util.*;

public class Directory
{
	private static int maxChars = 30; // max characters of each file name

	// Directory entries
	private int fsizes[];        // each element stores a different file size.
	private char fnames[][];    // each element stores a different file name.

	public Directory(int maxInumber)
	{
		fsizes = new int[maxInumber];     // maxInumber = max files
		for ( int i = 0; i < maxInumber; i++ ) 
			fsizes[i] = 0;                 // all file size initialized to 0
		fnames = new char[maxInumber][maxChars];
		String root = "/";                // entry(inode) 0 is "/"
		fsizes[0] = root.length( );        // fsizes[0] is the size of "/".
		root.getChars( 0, fsizes[0], fnames[0], 0 ); // fnames[0] includes "/"
	}

	public void bytes2directory(byte data[])
	{
	 // assumes data[] received directory information from disk
	 // initializes the Directory instance with this data[]
		int offset = 0;
		for (int i = 0; i < fsizes.length; i++)
		{
			offset += 4;
			fsizes[i] = SysLib.bytes2int(data, offset);
		}
		for (int i = 0; i < fnames.length; i++)
		{
			offset += maxChars * 2;
			String fname = new String (data, offset, maxChars * 2);
			fname.getChars(0, fsizes[i], fnames[i], 0);
		}
	}

	public byte[] directory2bytes()
	{
		// converts and return Directory information into a plain byte array
		// this byte array will be written back to disk
		// note: only meaningfull directory information should be converted
		// into bytes.

		int numberOfBytes = 0;

		// Number of bytes needed for all file sizes.
		// Note: each file size is an int, so 4 bytes.
		numberOfBytes += fsizes.length * 4;

		// Number of bytes needed for all file names.
		// Note: each character requires 2 bytes in Java.
		numberOfBytes += fsizes.length * maxChars * 2;

		byte[] buffer = new byte[numberOfBytes];
		short offset = 0;

		// Write all file sizes.
		for(int i = 0; i < fsizes.length; i++)
		{
			// Write the ith file size.
			SysLib.int2bytes(fsizes[i], buffer, offset);

			// Increase the offset past the file size.
			offset += 4;
		}

		// Write all file names.
		for(int i = 0; i < fsizes.length; i++)
		{
			for(int j = 0; j < maxChars; i++)
			{
				// Write the ith file's jth character to the buffer.
				SysLib.short2bytes((short)fnames[i][j], buffer, offset);

				// Increase the offset past the character.
				offset += 2;
			}
		}

		return buffer;
	}

	public short ialloc(String filename)
	{
		// filename is the one of a file to be created.
		// allocates a new inode number for this filename

		if(filename != null && !filename.isEmpty())
		{
			for(short i = 0; i < fsizes.length; i++)
			{
				if(fsizes[i] == 0)
				{
					fsizes[i] = filename.length();
					fnames[i] = filename.toCharArray();

					return i;
				}
			}
		}

		return -1;
	}

	public boolean ifree(short iNumber)
	{
		// deallocates this inumber (inode number)
		// the corresponding file will be deleted.

		if(iNumber >= 0 && iNumber < fsizes.length)
		{
			fsizes[iNumber] = 0;
			fnames[iNumber] = new char[maxChars];

			return true;
		}

		return false;
	}

	public short namei(String filename)
	{
		// returns the inumber corresponding to this filename

		String currentFilename = null;

		for(short i = 0; i < fsizes.length; i++)
		{
			currentFilename = new String(fnames[i], 0, fsizes[i]);

			if(filename.equals(currentFilename))
				return i;
		}

		return -1;
	}
}
