package SiteMap;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


public class SideConnection {
    /**
     * Print method that does something only if debug is true.
     *
     * @param logMsg the message to log
     */
    private static void dPrint(Object logMsg) {
        if (DEBUG) {
            System.out.println(logMsg);
        }
    }

    /**
     * Turned on if standard output debug message is desired.
     */
    private static final boolean DEBUG = true;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64)" +
            " AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.112 Safari/535.1";
    /**
     * The initial URL is the parent URL
     */
    private String initialURL;
    /**
     * All the links on the page.
     */
    private List<String[]> links = new LinkedList<String[]>();

    /**
     * current Webpage
     */
    private Document htmlDocument;
    /**
     * The database where everything is added too.
     */
    public static Database DB;
    /**
     * Determines the pages that are visited.
     */
    private Set<String> pagesVisited = new HashSet<String>();
    /**
     * Holds all the pages that still need to be visited.
     */
    private List<String[]> pagesToVisit = new LinkedList<String[]>();

    /**
     * Constructor for the side connection.
     *
     * @param initialURL The ultimate 'parent' URL
     * @throws SQLException if there is an issue in the sql code.
     */
    SideConnection(String initialURL) throws SQLException {
        this.initialURL = initialURL;
        this.DB = new Database();
        DB.runSql2("TRUNCATE Record;");
    }

    /**
     * Hash the URL, making it an int
     * Not great yet. Will work on it.
     *
     * @param URL - the URL that needs an Id assigned.
     * @return int for value into the storage table.
     */
    private int makeID(String URL) {
        // TODO: MAKE THIS BETTER
        return (URL.hashCode());
    }

    /**
     * Checks the database for the URL, if it is not present, it adds it.
     * Returns false if not present, true if it is. Adapted from online to
     * fit personal needs.
     *
     * @param URL -the URL value to see if it is in the database.
     * @return True if URL is present, False if it isn't and adds it to the
     * database.
     * @throws SQLException - in case of an SQL error.
     */
    private Boolean check(String URL, String text) throws SQLException {
        String sql =
                "SELECT * FROM `record` WHERE `RecordID`='" + makeID(URL) +
                        "';";
        ResultSet rs = DB.runSql(sql);
        if (rs.next()) {
            return true;
        } else {
            //store the URL so it is not used again.
            sql = "INSERT INTO `record` (`RecordID`, `URL`, `Page Title`) " +
                    "VALUES ('" + makeID(URL) + "', '" + URL + "', '" + text + "');";
            Statement stmt = DB.conn.createStatement();
            stmt.executeUpdate(sql);
            return false;
        }
    }

    /**
     * Creates a page for the website, where the top value is the parentURL
     * and text.
     *
     * @param URL the url for the page
     * @param text the text for the page
     * @throws SQLException in case SQL doesn't work.
     */
    private void createPageTable(String URL, String text, String parentURL,
                                 String parentTxt) throws SQLException{
        String sql = "CREATE TABLE `crawler`.`" + text +"` ( `PageTitle` TEXT" +
                " NOT NULL , `RecordID` INT NOT NULL , `URL` TEXT NOT NULL ) ENGINE = MyISAM;";
        Statement stmt = DB.conn.createStatement();
        stmt.executeUpdate(sql);

        sql = "INSERT INTO `record` (`RecordID`, `URL`, `Page Title`) " +
                "VALUES ('" + makeID(parentURL) + "', '" + parentURL + "', '" + parentTxt +
                "');";
        stmt.executeUpdate(sql);

        sql = "INSERT INTO `record` (`RecordID`, `URL`, `Page Title`) " +
                "VALUES ('" + makeID(URL) + "', '" + URL + "', '" + text + "');";
        stmt.executeUpdate(sql);
    }

    /**
     * Determines if the URL is a 'parent URL' of the code.
     *
     * @param urlToCheck the url that is being checked
     * @param parentURL  the original code
     * @return whether it is a parent or not.
     */
    private Boolean isParent(String urlToCheck, String parentURL) {
        if (urlToCheck.length() < parentURL.length()) {
            return false;
        }
        for (int i = 0; i < parentURL.length(); i++) {
            if (!(urlToCheck.charAt(i) == parentURL.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private Boolean inTable(String URL){
        return null;
    }

    /**
     * This sets up the recursive loop through a parent url.
     *
     * @throws SQLException In case the database finds an error.
     */
    void findPages() throws SQLException {
        String[] initialURLArray = new String[2];
        initialURLArray[0] = initialURL;
        initialURLArray[1] = "MAIN";
        pagesToVisit.add(initialURLArray);
        String sql;
        dPrint("First Insert");
        /**
        sql = "INSERT INTO `record` (`RecordID`, `URL`, `Page Title`) " +
                "VALUES ('" + makeID(initialURL) + "', '" + initialURL + "', " +
                "'" + "MAIN" + "');";
        Statement stmt = DB.conn.createStatement();
        stmt.executeUpdate(sql);
         */
        while (!pagesToVisit.isEmpty()) {
            String[] currentURL;
            currentURL = pagesToVisit.remove(0);
            dPrint("Checking: " + currentURL[0]);
            if (!check(currentURL[0], currentURL[1])) {
                crawl(currentURL[0]);
                this.pagesVisited.add(currentURL[0]);
                for (String[] page : getLinks()) {
                    if (isParent(page[0], initialURL)) {
                        pagesToVisit.add(page);
                    }
                }
            }
        }
    }

    /**
     * the thing that crawls along the website, finds links and names.
     *
     * @param URL the URL to crawl too
     * @return if it was successful or not.
     */
    boolean crawl(String URL) {
        try {
            Connection conn = Jsoup.connect((URL));
            conn.userAgent(USER_AGENT);
            Document htmlDoc = conn.get();
            dPrint("Crawling: " + URL);
            this.htmlDocument = htmlDoc;
            if (conn.response().statusCode() == 200) // 200 is the HTTP OK status
            // code
            {
                dPrint("\n**Visiting** Received web page at " + URL);
            }
            if (!conn.response().contentType().contains("text/html")) {
                dPrint("**Failure** Retrieved something other than HTML");
                return false;
            }
            Elements linksOnPage = htmlDocument.select("a[href]");
            dPrint("Found (" + linksOnPage.size() + ") links");
            links = new LinkedList<String[]>();
            for (Element link : linksOnPage) {
                String[] addedlink = new String[2];
                addedlink[0] = link.absUrl("href");
                addedlink[1] = link.text();
                this.links.add(addedlink);
            }
            return true;
        } catch (IOException ioe) {
            // We were not successful in our HTTP request
            return false;
        }
    }

    private List<String[]> getLinks() {
        return this.links;
    }


}
