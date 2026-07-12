import java.sql.*;
import java.util.*;

public class TestShowTables {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://124.223.220.245:3306/wushi?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        try (Connection con = DriverManager.getConnection(url, "root", "astock_root")) {
            DatabaseMetaData md = con.getMetaData();
            ResultSet rs = md.getTables("wushi", null, "%", new String[]{"TABLE"});
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
            Collections.sort(tables);
            System.out.println("Total tables: " + tables.size());
            for (String t : tables) System.out.println("  - " + t);
        }
    }
}
