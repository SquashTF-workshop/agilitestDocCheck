import org.testng.annotations.Test;
import org.openqa.selenium.Keys;
import com.ats.executor.ActionTestScript;
import com.ats.script.actions.*;
import com.ats.generator.objects.Cartesian;
import com.ats.generator.objects.mouse.Mouse;
import com.ats.tools.Operators;
import com.ats.generator.variables.Variable;

public class getRunnerNamePrem extends ActionTestScript{

	/**
	 * Test Name : <b>getRunnerNamePrem</b>
	 * Test Author : <b>NX-DOMAIN\kung</b>
	 * Test Description : <i></i>
	 * Test Prerequisites : <i></i>
	 */

	@Test
	public void testMain(){
		// -----------------------------------------------
		// Get parameters passed by the calling script :
		// getParameter(int index)
		// -----------------------------------------------
		// String param0 = getParameter(0).toString();
		// int param0 = getParameter(0).toInt();
		// double param0 = getParameter(0).toDouble();
		// boolean param0 = getParameter(0).toBoolean();
		// -----------------------------------------------
		// int it = getIteration(); -> return current iteration loop
		// String path = getCsvFilePath(); -> return csv file path sent as parameter to call current script
		// File file = getCsvFile(); -> return csv file sent as parameter to call current script
		// File file = getAssetsFile("[relative path string]"); -> return a file in the project's 'assets' folder
		// String url = getAssetsUrl("[relative path string]"); -> return url path of a file in the project's 'assets' folder
		
                int tcCuf = getParameter(0).toInt();
                int tsCuf = getParameter(1).toInt();
                int cpgCuf = getParameter(2).toInt();
                int itCuf = getParameter(3).toInt();
                int dsName = getParameter(4).toInt();
                int dsParam = getParameter(5).toInt();
		
                if (!getParameter(0).toString().equals("testcase")){
                  throw new Exception (getParameter(0) + " is not equal to testcase");
                }
                if (!getParameter(1).toString().equals("testsuite")){
                  throw new Exception (getParameter(1) + " is not equal to testsuite");
                }
                if (!getParameter(2).toString().equals("campaign")){
                  throw new Exception (getParameter(2) + " is not equal to campaign");
                }
                if (!getParameter(3).toString().equals("iteration")){
                  throw new Exception (getParameter(0) + " is not equal to iteration");
                }
                if (!getParameter(4).toString().equals("dataset1")){
                  throw new Exception (getParameter(1) + " is not equal to dataset1");
                }
                if (!getParameter(5).toString().equals("dsvalue1")){
                  throw new Exception (getParameter(2) + " is not equal to dsvalue1");
                }                

                returnValues("Squash TF Java Junit Runner");

		
		
		
		
		
		// -----------------------------------------------
		// Return string values to calling script :
		// returnValues(String ...)
		// -----------------------------------------------
		// returnValues("value", stringVariable);
	}
}