        // AudioInput lineIn = new Minim(new JSMinim(this)).getLineIn();
        BpmFinder bpmFinder = new BpmFinder(/*lineIn*/);
                
                // wait for data processing...
                try { Thread.sleep(5000); } catch (InterruptedException e) {}
                
                // print out tempo
                System.out.println(bpmFinder.getAverageBpm() + "bpm");