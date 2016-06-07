package bufmgr;

import global.GlobalConst;
import global.Minibase;
import global.Page;
import global.PageId;

import java.util.HashMap;

/**
 * <h3>Minibase Buffer Manager</h3>
 * The buffer manager reads disk pages into a main memory page as needed. The
 * collection of main memory pages (called frames) used by the buffer manager
 * for this purpose is called the buffer pool. This is just an array of Page
 * objects. The buffer manager is used by access methods, heap files, and
 * relational operators to read, write, allocate, and de-allocate pages.
 */
public class BufMgr implements GlobalConst {

  /** Actual pool of pages (can be viewed as an array of byte arrays). */
  protected Page[] bufpool;

  /** Array of descriptors, each containing the pin count, dirty status, etc. */
  protected FrameDesc[] frametab;

  /** Maps current page numbers to frames; used for efficient lookups. */
  protected HashMap<Integer, FrameDesc> pagemap;

  /** The replacement policy to use. */
  protected Replacer replacer;

  // --------------------------------------------------------------------------

  /**
   * Constructs a buffer mamanger with the given settings.
   * 
   * @param numbufs number of buffers in the buffer pool
   */
  public BufMgr(int numbufs) {

    // initialize the buffer pool and frame table
    bufpool = new Page[numbufs];
    frametab = new FrameDesc[numbufs];
    for (int i = 0; i < numbufs; i++) {
      bufpool[i] = new Page();
      frametab[i] = new FrameDesc(i);
    }

    // initialize the specialized page map and replacer
    pagemap = new HashMap<Integer, FrameDesc>(numbufs);
    replacer = new Clock(this);

  } // public BufMgr(int numbufs)

  /**
   * Allocates a set of new pages, and pins the first one in an appropriate
   * frame in the buffer pool.
   * 
   * @param firstpg holds the contents of the first page
   * @param run_size number of pages to allocate
   * @return page id of the first new page
   * @throws IllegalArgumentException if PIN_MEMCPY and the page is pinned
   * @throws IllegalStateException if all pages are pinned (i.e. pool exceeded)
   */
  public PageId newPage(Page firstpg, int run_size) {

    // allocate the run
    PageId firstid = Minibase.DiskManager.allocate_page(run_size);

    // try to pin the first page
    try {
      pinPage(firstid, firstpg, PIN_MEMCPY);
    } catch (RuntimeException exc) {

      // roll back because pin failed
      for (int i = 0; i < run_size; i++) {
        firstid.pid += i;
        Minibase.DiskManager.deallocate_page(firstid);
      }

      // re-throw the exception
      throw exc;
    }

    // notify the replacer and return the first new page id
    replacer.newPage(pagemap.get(firstid.pid));
    return firstid;

  } // public PageId newPage(Page firstpg, int run_size)

  /**
   * Deallocates a single page from disk, freeing it from the pool if needed.
   * 
   * @param pageno identifies the page to remove
   * @throws IllegalArgumentException if the page is pinned
   */
  public void freePage(PageId pageno) {

    // get the frame descriptor, if the page is in the buffer pool
    FrameDesc fdesc = pagemap.get(pageno.pid);
    if (fdesc != null) {

      // first check the frame state, if it's pinned -> throw a new exception with this message: "Page currently pinned"
    	  
    		  if(fdesc.pincnt>0)
    		  {
    			  throw new IllegalArgumentException("Page currently pinned");
    		  }  	 
      // remove the page from the buffer pool, update pin count, dirty bit, and page no
    		//bufpool[index]=new PageId().pid;
    		  pagemap.remove(fdesc.pageno.pid);
    		  fdesc.pincnt=0;
    		  fdesc.dirty=false;
    		  fdesc.pageno.pid= INVALID_PAGEID;
      // notify the replacer
    		  replacer.freePage(fdesc);
    	}
 // if in pool

    // deallocate the page from disk
  
  Minibase.DiskManager.deallocate_page(pageno);
   // public void freePage(PageId firstid)
  }
  /**
   * Pins a disk page into the buffer pool. If the page is already pinned, this
   * simply increments the pin count. Otherwise, this selects another page in
   * the pool to replace, flushing it to disk if dirty.
   * 
   * @param pageno identifies the page to pin
   * @param page holds contents of the page, either an input or output param
   * @param skipRead PIN_MEMCPY (replace in pool); PIN_DISKIO (read the page in)
 * @return 
   * @throws IllegalArgumentException if PIN_MEMCPY and the page is pinned
   * @throws IllegalStateException if all pages are pinned (i.e. pool exceeded)
   */
  public  void pinPage(PageId pageno, Page page,boolean skipRead) {
    // first check if the page is already pinned
    FrameDesc fdesc = pagemap.get(pageno.pid);
    if (fdesc != null) {
    	      // validate the pin method
      if (skipRead) {
       throw new IllegalArgumentException("Page pinned; PIN_MEMCPY not allowed");
      			}

      // increment the pin count, notify the replacer
      fdesc.pincnt++;
      replacer.pinPage(fdesc);
  //wrap the buffer
      page.setPage(bufpool[fdesc.index]);
      return;

    
    }// if in pool

    // ask replacer to select an available frame
	// if the frame number returned by replacer is smaller than 0 -> throw a new exception with this message: "Buffer pool exceeded"
    int frameno = replacer.pickVictim();
      if(frameno<0)
      {
    	  throw new IllegalStateException("Buffer pool exceeded");
      }

    fdesc = frametab[frameno];

    // System.out.println("BufMgr PINPAGE: " + skipRead + ", pageId = "
    // + pageno.pid + ", frameId = " + frameno);

    // check if the page no is valid? 
    if (pageno.pid != INVALID_PAGEID) {
	// if yes remove it!
      pagemap.remove(fdesc.pageno.pid);
	  //if the frame was in use and dirty, write it to disk
      if (fdesc.dirty) {
		//writing it on disk
        Minibase.DiskManager.write_page(fdesc.pageno, bufpool[frameno]);
      }
    
      }
    // read in the page if requested, and wrap the buffer
    if (skipRead) {
      bufpool[frameno].copyPage(page);
    } else {
      Minibase.DiskManager.read_page(pageno, bufpool[frameno]);
    }
    page.setPage(bufpool[frameno]);

    // update the frame descriptor -> update pid, pin count and dirty bit
    fdesc.pageno.pid= pageno.pid;
      fdesc.pincnt++;
      fdesc.dirty=true;
    // update the page map
    pagemap.put(pageno.pid, fdesc);
	// notify the replacer
    replacer.pinPage(fdesc);
    }
   // public void pinPage(PageId pageno, Page page, boolean skipRead)

  /**
   * Unpins a disk page from the buffer pool, decreasing its pin count.
   * 
   * @param pageno identifies the page to unpin
   * @param dirty UNPIN_DIRTY if the page was modified, UNPIN_CLEAN otherrwise
   * @throws IllegalArgumentException if the page is not present or not pinned
   */
  public void unpinPage(PageId pageno, boolean dirty) {

    // get the frame descriptor for the page
     /*???*/FrameDesc fdesc = pagemap.get(pageno.pid);
	if(fdesc==null){
	// check if page is in pool? if not throw an IllegalArgumentException exception with this message: "Page not present"
     
         throw new IllegalArgumentException(
             "Page not present");
       }

    // check the frame state, if it's unpinned, throw an IllegalArgumentException exception with this message: "Page not pinned"
     if(fdesc.state== 10 || fdesc.state==11)
	 {
     throw new IllegalArgumentException("Page not pinned");  
	}  	

    // update the frame descriptor -> update the pin count and dirty bit of frame
    fdesc.pincnt--;
    fdesc.dirty=dirty;
    // notify the replacer
    replacer.unpinPage(pagemap.get(pageno.pid));
  } // public void unpinPage(PageId pageno, boolean dirty)

  /**
   * Immediately writes a page in the buffer pool to disk, if dirty.
   */
  public void flushPage(PageId pageno) {
    flushPages(pageno);
  }

  /**
   * Immediately writes all dirty pages in the buffer pool to disk.
   */
  public void flushAllPages() {
    flushPages(null);
  }

  /**
   * Helper method to flush a dirty page or all dirty pages to disk.
   * 
   * @param pageno identifies to the page to flush, or null if all pages
   */
  protected void flushPages(PageId pageno) {

    // iterate the buffer pool
    for (int i = 0; i < bufpool.length; i++) {
    	

      // write all valid dirty pages to disk
      if ((pageno == null) || (frametab[i].pageno.pid == pageno.pid)) {
        // if the page is dirty
    	 
        if(frametab[i].dirty)     
        {

          // then write the page to disk
          Minibase.DiskManager.write_page(frametab[i].pageno, bufpool[i]);
          frametab[i].pincnt=0;
          frametab[i].dirty = false;
        }
          // and the buffer page is now clean, so update it!
    

} // if candidate page
    } // for each frame

  } // protected void flushPages(PageId pageno)

  /**
   * Gets the total number of buffer frames.
   */
  public int getNumBuffers() {
    return bufpool.length;
  }

  /**
   * Gets the total number of unpinned buffer frames.
   */
  public int getNumUnpinned() {
    int cnt = 0;
   final int size=getNumBuffers();
   for (int val = 0; val < size; val++) {
       if (frametab[val].pincnt == 0) {
           cnt++;
       }
   } 
    return cnt;
  }

} // public class BufMgr implements GlobalConst
