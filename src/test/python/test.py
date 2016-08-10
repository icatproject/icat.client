import unittest
import time
import os

from icat.ICAT import ICAT, IcatException

class IcatTest(unittest.TestCase):
    
    def setUp(self):
        self.icat = ICAT(os.environ["serverUrl"], os.environ["serverCert"])
        self.session = self.icat.login("db", {"username":"root", "password":"password"})
        for fid in self.session.search("SELECT f.id from Facility f"):
            f = {"Facility": {"id" : fid}}
            self.session.delete(f)
        facility = {}
        facility["name"] = "Test Facility"
        entity = {"Facility":facility}
        fid = self.session.write(entity)[0]

        investigationType = {}
        investigationType["facility"] = {"id":fid}
        investigationType["name"] = "E"
        entity = {"InvestigationType" : investigationType}
        itid = self.session.write(entity)[0]
             
        entities = []
        for name in ["Inv 1", "Inv 2", "Inv 3"]:
            investigation = {}
            investigation["facility"] = {"id":fid}
            investigation["type"] = {"id":itid}
            investigation["name"] = name
            investigation["title"] = "The " + name
            investigation["visitId"] = "One"
            entities.append({"Investigation": investigation})
        self.session.write(entities)
        
        datasetType = {"name" :"DS Type", "facility" : {"id":fid}}
        entity = {"DatasetType" : datasetType}
        self.session.write(entity)
        
    def testInfo(self):
        version = self.icat.getApiVersion()["version"]
        self.assertTrue(version.startswith("4.8"))
 
    def testSession(self):
        icat = ICAT(os.environ["serverUrl"], os.environ["serverCert"])
        self.assertFalse(icat.isLoggedIn("mnemonic/rubbish"))
        self.assertFalse(icat.isLoggedIn("rubbish"))
        credentials = {}
        credentials["username"] = "notroot"
        credentials["password"] = "password"
        session = icat.login("db", credentials)
        self.assertEqual("db/notroot", session.getUserName())
        remainingMinutes = session.getRemainingMinutes()
        self.assertTrue(remainingMinutes > 119 and remainingMinutes < 120)
        self.assertTrue(icat.isLoggedIn("db/notroot"))
        session.logout();
 
        try:
            session.getRemainingMinutes();
            self.fail();
        except IcatException as e:
            self.assertEquals(IcatException.SESSION, e.getType());
            
        session = icat.login("db", credentials);
        time.sleep(1);
        remainingMinutes = session.getRemainingMinutes();
        session.refresh();
        self.assertGreater(session.getRemainingMinutes(), remainingMinutes);
        
    def testWrite(self):
        invid = self.session.search("SELECT i.id FROM Investigation i WHERE i.facility.name = 'Test Facility' AND i.name = 'Inv 1'")[0]
        dstid = self.session.search("SELECT d.id FROM DatasetType d")[0]
        dataset = {"name" : "ds1", "investigation" : { "id" : invid}, "type": {"id":dstid}}
        dataset["datafiles"] = [{"name" : "df1"}, {"name":"df2"}]
        entity = {"Dataset" : dataset}
        dsid = self.session.write(entity)[0]
        self.assertEquals([2], self.session.search("SELECT COUNT(df) FROM Datafile df"))
        
        dataset["id"] = dsid
        dataset["datafiles"] = [{"name" : "df3"}]
        dataset["description"] = "Indescribable"
        mt = self.session.write(entity)
        self.assertEquals([], mt)
        
        self.assertEquals([3], self.session.search("SELECT COUNT(df) FROM Datafile df"))
        self.assertEquals(["Test Facility"], self.session.search("SELECT df.dataset.investigation.facility.name FROM Datafile df WHERE df.name = 'df3'"))
        
        
    def testSearch(self):
        f = self.session.search("SELECT f.id, f.name from Facility f")[0]
        self.assertEquals("Test Facility", f[1])
        fid = f[0]

        f = self.session.search("SELECT f from Facility f")[0] 
        self.assertEquals("Test Facility", f["Facility"]["name"])
        self.assertEquals(0, len(f["Facility"]["investigations"]))

        f = self.session.search("SELECT f from Facility f INCLUDE f.investigations")[0]  
        self.assertEquals("Test Facility", f["Facility"]["name"])
        self.assertEquals(3, len(f["Facility"]["investigations"]))
        
        f = self.session.get("Facility f INCLUDE f.investigations", fid)
        self.assertEquals("Test Facility", f["Facility"]["name"])
        self.assertEquals(3, len(f["Facility"]["investigations"]))
        
        try:
            f = self.session.get("Facility f INCLUDE f.investigation", fid)
            self.fail()
        except IcatException as e:
            self.assertEquals(IcatException.BAD_PARAMETER, e.getType())
        
    def testDelete(self):
        self.assertEquals([1], self.session.search("SELECT COUNT(f) FROM Facility f"))
        for fid in self.session.search("SELECT f.id from Facility f"):
            f = {"Facility": {"id" : fid}}
            self.session.delete(f)
        self.assertEquals([0], self.session.search("SELECT COUNT(f) FROM Facility f"))
        
if __name__ == '__main__':
    unittest.main()


