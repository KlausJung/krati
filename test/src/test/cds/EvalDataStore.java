package test.cds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import test.AbstractTest;

import krati.cds.store.DataStore;

/**
 * EvalDataStore.
 * 
 * @author jwu
 */
public abstract class EvalDataStore extends AbstractTest
{
    protected DataStore<byte[], byte[]> _store;
    
    static List<String> _lineSeedData = new ArrayList<String>(3000);
    
    static class Reader implements Runnable
    {
        DataStore<byte[], byte[]> _ds;
        Random _rand = new Random();
        
        int _dataCnt = _lineSeedData.size();
        volatile boolean _running = true;
        volatile long _cnt = 0;
        
        public Reader(DataStore<byte[], byte[]> ds)
        {
            this._ds = ds;
        }
        
        public long getReadCount()
        {
            return this._cnt;
        }

        public void stop()
        {
            _running = false;
        }
        
        void read()
        {
            String key = _lineSeedData.get(_rand.nextInt(_dataCnt));
            int keyLength = 30 + (_rand.nextInt(100) * 3);
            if(key.length() > keyLength) {
                key = key.substring(0, keyLength);
                _ds.get(key.getBytes());
                _cnt++;
            }
        }
        
        @Override
        public void run()
        {
            while(_running)
            {
                read();
            }
        }
    }
    
    static class Checker extends Reader
    {
        public Checker(DataStore<byte[], byte[]> ds)
        {
            super(ds);
        }
        
        void read()
        {
            String line = _lineSeedData.get(_rand.nextInt(_dataCnt));
            int keyLength = 30 + (_rand.nextInt(100) * 3);
            if(line.length() > keyLength) {
                String key = line.substring(0, keyLength);
                byte[] val = _ds.get(key.getBytes());
                if(val != null) {
                    String lineRead = new String(val);
                    if(!line.equals(lineRead)) {
                        System.err.printf("key=\"%s\"%n", key);
                        System.err.printf("    \"%s\"%n", line);
                        System.err.printf("    \"%s\"%n", lineRead);
                    }
                    assertTrue("key=" + key + ", value=" + line, line.equals(lineRead));
                }
                _cnt++;
            }
        }
    }
    
    static class Writer implements Runnable
    {
        DataStore<byte[], byte[]> _ds;
        Random _rand = new Random();
        
        int _dataCnt;
        volatile boolean _running = true;
        volatile long _cnt = 0;
        
        public Writer(DataStore<byte[], byte[]> ds)
        {
            this._ds = ds;
            this._dataCnt = _lineSeedData.size();
        }
        
        public long getWriteCount()
        {
            return this._cnt;
        }

        public void stop()
        {
            _running = false;
        }
        
        void write()
        {
            try
            {
                String value = _lineSeedData.get(_rand.nextInt(_dataCnt));
                int keyLength = 30 + ( _rand.nextInt(100) * 3);
                if(value.length() > keyLength) {
                    String key = value.substring(0, keyLength);
                    _ds.put(key.getBytes(), value.getBytes());
                    _cnt++;
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        
        @Override
        public void run()
        {
            while(_running)
            {
                write();
            }
        }
    }
    
    public EvalDataStore(String name)
    {
        super(name);
    }
    
    public void loadSeedData(File dataFile) throws IOException
    {
        String line;
        FileReader reader = new FileReader(dataFile);
        BufferedReader in = new BufferedReader(reader);
        
        while((line = in.readLine()) != null)
        {
            _lineSeedData.add(line);
        }
        
        in.close();
        reader.close();
    }
    
    public void populate(DataStore<byte[], byte[]> ds) throws Exception
    {
        int count = 0;
        
        long startTime = System.currentTimeMillis();
        
        for(int i = 0; i < 100; i++)
        {
            for(String line : _lineSeedData)
            {
                if(line.length() > (30 + i * 3))
                {
                    count++;
                    String key = line.substring(0, 30 + i * 3);
                    ds.put(key.getBytes(), line.getBytes());
                }
            }
        }
        
        ds.persist();
        
        long endTime = System.currentTimeMillis();
        long elapsedTime = endTime - startTime;
        System.out.printf("elapsedTime=%d ms (init)%n", elapsedTime);
        
        double rate = count/(double)elapsedTime;
        System.out.printf("writeCount=%d rate=%6.2f per ms%n", count, rate);
    }
    
    public void validate(DataStore<byte[], byte[]> ds) throws Exception
    {
        int count = 0;
        
        for(int i = 0; i < 100; i++)
        {
            for(String line : _lineSeedData)
            {
                if(line.length() > (30 + i * 3))
                {
                    count++;
                    String key = line.substring(0, 30 + i * 3);
                    byte[] value = ds.get(key.getBytes());
                    if(value != null) {
                        String lineRead = new String(value);
                        if(!line.equals(lineRead)) {
                            System.err.printf("key=\"%s\"%n", key);
                            System.err.printf("    \"%s\"%n", line);
                            System.err.printf("    \"%s\"%n", lineRead);
                        }
                    }
                }
            }
        }
        
        System.out.println("OK");
    }

    public void evalWrite(DataStore<byte[], byte[]> ds, int writerCnt, int runDuration) throws Exception
    {
        try
        {
            // Start writers
            Writer[] writers = new Writer[writerCnt];
            for(int i = 0; i < writers.length; i++)
            {
                writers[i] = new Writer(ds);
            }
            
            Thread[] writerThreads = new Thread[writers.length];
            for(int i = 0; i < writerThreads.length; i++)
            {
                writerThreads[i] = new Thread(writers[i]);
                writerThreads[i].start();
                System.out.println("Writer " + i + " started");
            }
            
            long startTime = System.currentTimeMillis();
            long writeCount = 0;
            int heartBeats = runDuration/10;
            for(int i = 0; i < heartBeats; i++)
            {
                Thread.sleep(10000);
                long newWriteCount = 0;
                for(int r = 0; r < writers.length; r++)
                {
                    newWriteCount += writers[r].getWriteCount();
                }
                
                System.out.printf("writeCount=%d%n", newWriteCount - writeCount);
                writeCount = newWriteCount;
            }
            
            // Stop writer
            for(int i = 0; i < writers.length; i++)
            {
                writers[i].stop();
            }
            for(int i = 0; i < writerThreads.length; i++)
            {
                writerThreads[i].join();
            }
            
            long endTime = System.currentTimeMillis();

            long elapsedTime = endTime - startTime;
            System.out.printf("elapsedTime=%d ms%n", elapsedTime);
            
            double sumWriteRate = 0;
            for(int i = 0; i < writers.length; i++)
            {
                double rate = writers[i].getWriteCount()/(double)elapsedTime;
                System.out.printf("writeCount[%d]=%d rate=%6.2f per ms%n", i, writers[i].getWriteCount(), rate);
                sumWriteRate += rate;
            }

            System.out.printf("Total Write Rate=%6.2f per ms%n", sumWriteRate);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    public void evalRead(DataStore<byte[], byte[]> ds, int readerCnt, int runDuration) throws Exception
    {
        try
        {
            // Start readers
            Reader[] readers = new Reader[readerCnt];
            for(int i = 0; i < readers.length; i++)
            {
                readers[i] = new Reader(ds);
            }
            
            Thread[] threads = new Thread[readers.length];
            for(int i = 0; i < threads.length; i++)
            {
                threads[i] = new Thread(readers[i]);
                threads[i].start();
                System.out.println("Reader " + i + " started");
            }
            
            long startTime = System.currentTimeMillis();
            
            // Sleep until run time is exhausted
            Thread.sleep(runDuration * 1000);
            
            for(int i = 0; i < readers.length; i++)
            {
                readers[i].stop();
            }
            for(int i = 0; i < threads.length; i++)
            {
                threads[i].join();
            }
            
            long endTime = System.currentTimeMillis();
            
            double sumReadRate = 0;
            long elapsedTime = endTime - startTime;
            System.out.printf("elapsedTime=%d ms%n", elapsedTime);
            for(int i = 0; i < readers.length; i++)
            {
                double rate = readers[i].getReadCount()/(double)elapsedTime;
                System.out.printf("readCount[%d]=%d rate=%6.2f per ms%n", i, readers[i].getReadCount(), rate);
                sumReadRate += rate;
            }
            
            System.out.printf("Total Read Rate=%6.2f per ms%n", sumReadRate);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    public void evalReadWrite(DataStore<byte[], byte[]> ds, int readerCnt, int writerCnt, int runDuration, boolean doValidation) throws Exception
    {
        try
        {
            // Start readers
            Reader[] readers = new Reader[readerCnt];
            for(int i = 0; i < readers.length; i++)
            {
                readers[i] = doValidation ? new Checker(ds) : new Reader(ds);
            }

            Thread[] readerThreads = new Thread[readers.length];
            for(int i = 0; i < readerThreads.length; i++)
            {
                readerThreads[i] = new Thread(readers[i]);
                readerThreads[i].start();
                System.out.println("Reader " + i + " started");
            }
            
            // Start writers
            Writer[] writers = new Writer[writerCnt];
            for(int i = 0; i < writers.length; i++)
            {
                writers[i] = new Writer(ds);
            }
            
            Thread[] writerThreads = new Thread[writers.length];
            for(int i = 0; i < writerThreads.length; i++)
            {
                writerThreads[i] = new Thread(writers[i]);
                writerThreads[i].start();
                System.out.println("Writer " + i + " started");
            }
            
            long startTime = System.currentTimeMillis();
            
            long readCount = 0;
            long writeCount = 0;
            int heartBeats = runDuration/10;
            for(int i = 0; i < heartBeats; i++)
            {
                Thread.sleep(10000);

                int newReadCount = 0;
                for(int r = 0; r < readers.length; r++)
                {
                    newReadCount += readers[r].getReadCount();
                }
                
                long newWriteCount = 0;
                for(int r = 0; r < writers.length; r++)
                {
                    newWriteCount += writers[r].getWriteCount();
                }
                
                System.out.printf("write=%d read=%d%n", newWriteCount-writeCount, newReadCount-readCount);
                
                readCount = newReadCount;
                writeCount = newWriteCount;
            }
            
            // Stop reader
            for(int i = 0; i < readers.length; i++)
            {
                readers[i].stop();
            }
            for(int i = 0; i < readerThreads.length; i++)
            {
                readerThreads[i].join();
            }
            
            // Stop writer
            for(int i = 0; i < writers.length; i++)
            {
                writers[i].stop();
            }
            for(int i = 0; i < writerThreads.length; i++)
            {
                writerThreads[i].join();
            }
            
            long endTime = System.currentTimeMillis();

            long elapsedTime = endTime - startTime;
            System.out.printf("elapsedTime=%d ms%n", elapsedTime);
            
            double sumWriteRate = 0;
            for(int i = 0; i < writers.length; i++)
            {
                double rate = writers[i].getWriteCount()/(double)elapsedTime;
                System.out.printf("writeCount[%d]=%d rate=%6.2f per ms%n", i, writers[i].getWriteCount(), rate);
                sumWriteRate += rate;
            }

            System.out.printf("Total Write Rate=%6.2f per ms%n", sumWriteRate);
            
            double sumReadRate = 0;
            for(int i = 0; i < readers.length; i++)
            {
                double rate = readers[i].getReadCount()/(double)elapsedTime;
                System.out.printf("readCount[%d]=%d rate=%6.2f per ms%n", i, readers[i].getReadCount(), rate);
                sumReadRate += rate;
            }
            
            System.out.printf("Total Read Rate=%6.2f per ms%n", sumReadRate);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }
    
    public static String getData(DataStore<byte[], byte[]> ds, String key)
    {
        String s = null;
        byte[] b = ds.get(key.getBytes());
        if (b != null) s = new String(b);
        return s;
    }
    
    public void run(int numOfReaders, int numOfWriters)
    {
        run(numOfReaders, numOfWriters, runTimeSeconds);
    }
    
    public void run(int numOfReaders, int numOfWriters, int runDuration)
    {
        try
        {
            File seedDataFile = new File(TEST_DIR, "seed/seed.dat");
            loadSeedData(seedDataFile);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return;
        }
        
        try
        {
            int timeAllocated = runDuration/3;
            
            File DataStoreDir = new File(TEST_OUTPUT_DIR, getClass().getSimpleName());
            _store = getDataStore(DataStoreDir);
            
            System.out.println("---populate---");
            populate(_store);
            
            System.out.println("---validate---");
            validate(_store);

            System.out.println("---testRead---");
            evalRead(_store, numOfReaders, 10);
            
            System.out.println("---testWrite---");
            evalWrite(_store, numOfWriters, timeAllocated);
            _store.persist();
            
            System.out.println("---validate---");
            validate(_store);
            
            System.out.println("---testReadWrite---");
            evalReadWrite(_store, numOfReaders, numOfWriters, timeAllocated, false);
            _store.persist();
            
            System.out.println("---validate---");
            validate(_store);

            System.out.println("---testWriteCheck---");
            evalReadWrite(_store, numOfReaders, numOfWriters, timeAllocated, true);
            _store.persist();
            
            System.out.println("---validate---");
            validate(_store);
            
            _store.sync();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
    
    abstract protected DataStore<byte[], byte[]> getDataStore(File DataStoreDir) throws Exception;
}
