package com.supermap.udbx.viewer;

import com.supermap.udbx.UdbxDataSource;
import com.supermap.udbx.core.DatasetInfo;
import com.supermap.udbx.core.DatasetType;
import com.supermap.udbx.dataset.CadDataset;
import com.supermap.udbx.dataset.CadFeature;
import com.supermap.udbx.dataset.Dataset;
import com.supermap.udbx.dataset.LineDataset;
import com.supermap.udbx.dataset.LineFeature;
import com.supermap.udbx.dataset.LineZDataset;
import com.supermap.udbx.dataset.PointDataset;
import com.supermap.udbx.dataset.PointFeature;
import com.supermap.udbx.dataset.PointZDataset;
import com.supermap.udbx.dataset.RegionDataset;
import com.supermap.udbx.dataset.RegionFeature;
import com.supermap.udbx.dataset.RegionZDataset;
import com.supermap.udbx.dataset.TabularDataset;
import com.supermap.udbx.dataset.TabularRecord;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UDBX 文件可视化查看器。
 *
 * <p>提供基于 Swing 的图形界面，支持打开 .udbx 文件、浏览数据集列表，
 * 并以表格形式展示各数据集中的要素数据。
 *
 * <p>运行方式：在 IDE 中直接运行 {@link #main(String[])} 方法，或使用：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass=com.supermap.udbx.viewer.UdbxViewerApp
 * </pre>
 */
public class UdbxViewerApp extends JFrame {

    private UdbxDataSource currentDataSource;
    private String currentFilePath;

    private JTree datasetTree;
    private JTable dataTable;
    private JLabel statusBar;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new UdbxViewerApp().setVisible(true);
        });
    }

    public UdbxViewerApp() {
        super("UDBX 查看器");
        buildUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeDataSource();
            }
        });
    }

    // ── UI 构建 ──────────────────────────────────────────────────────────────

    private void buildUI() {
        setJMenuBar(buildMenuBar());

        // 左侧：数据集树
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("请打开 .udbx 文件");
        datasetTree = new JTree(new DefaultTreeModel(root));
        datasetTree.setRootVisible(true);
        datasetTree.setCellRenderer(new DatasetTreeCellRenderer());
        datasetTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onTreeClick();
            }
        });
        JScrollPane treeScroll = new JScrollPane(datasetTree);
        treeScroll.setPreferredSize(new Dimension(260, 0));
        treeScroll.setBorder(BorderFactory.createTitledBorder("数据集"));

        // 右侧：数据表格
        dataTable = new JTable();
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        dataTable.setFillsViewportHeight(true);
        JScrollPane tableScroll = new JScrollPane(dataTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("数据"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, tableScroll);
        splitPane.setDividerLocation(260);
        splitPane.setResizeWeight(0.0);

        // 底部状态栏
        statusBar = new JLabel(" 就绪");
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");

        JMenuItem openItem = new JMenuItem("打开文件...");
        openItem.setAccelerator(KeyStroke.getKeyStroke("meta O"));
        openItem.addActionListener(e -> openFile());

        JMenuItem closeItem = new JMenuItem("关闭文件");
        closeItem.addActionListener(e -> closeFile());

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> {
            closeDataSource();
            System.exit(0);
        });

        fileMenu.add(openItem);
        fileMenu.add(closeItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        return menuBar;
    }

    // ── 文件操作 ──────────────────────────────────────────────────────────────

    private void openFile() {
        JFileChooser chooser = new JFileChooser(new File(System.getProperty("user.dir")));
        chooser.setDialogTitle("打开 UDBX 文件");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "UDBX 空间数据文件 (*.udbx)", "udbx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        loadFile(file.getAbsolutePath());
    }

    private void loadFile(String path) {
        closeDataSource();
        try {
            currentDataSource = UdbxDataSource.open(path);
            currentFilePath = path;
            List<DatasetInfo> infos = currentDataSource.listDatasetInfos();
            populateTree(new File(path).getName(), infos);
            clearTable();
            setStatus("已打开：" + path + "（共 " + infos.size() + " 个数据集）");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "无法打开文件：\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            setStatus("打开失败：" + path);
        }
    }

    private void closeFile() {
        closeDataSource();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("请打开 .udbx 文件");
        datasetTree.setModel(new DefaultTreeModel(root));
        clearTable();
        setStatus(" 就绪");
    }

    private void closeDataSource() {
        if (currentDataSource != null) {
            try {
                currentDataSource.close();
            } catch (Exception ignored) {
            }
            currentDataSource = null;
            currentFilePath = null;
        }
    }

    // ── 树操作 ────────────────────────────────────────────────────────────────

    private void populateTree(String filename, List<DatasetInfo> infos) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(filename);
        for (DatasetInfo info : infos) {
            String label = info.datasetName() + "  (" + info.objectCount() + " 条)";
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                    new DatasetNode(label, info));
            root.add(node);
        }
        datasetTree.setModel(new DefaultTreeModel(root));
        // 展开根节点
        datasetTree.expandRow(0);
    }

    private void onTreeClick() {
        var path = datasetTree.getSelectionPath();
        if (path == null) return;
        Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (last instanceof DatasetNode dn) {
            loadDatasetFeatures(dn.info());
        }
    }

    // ── 数据加载 ──────────────────────────────────────────────────────────────

    private void loadDatasetFeatures(DatasetInfo info) {
        if (currentDataSource == null) return;
        setStatus("正在加载 " + info.datasetName() + " ...");
        try {
            Dataset dataset = currentDataSource.getDataset(info.datasetName());
            if (dataset == null) {
                setStatus("数据集不存在：" + info.datasetName());
                return;
            }
            switch (info.datasetType()) {
                case Point   -> showPointFeatures(((PointDataset) dataset).getFeatures(), dataset.getName(), "点数据集");
                case PointZ  -> showPointFeatures(((PointZDataset) dataset).getFeatures(), dataset.getName(), "3D 点数据集");
                case Line    -> showLineFeatures(((LineDataset) dataset).getFeatures(), dataset.getName(), "线数据集");
                case LineZ   -> showLineFeatures(((LineZDataset) dataset).getFeatures(), dataset.getName(), "3D 线数据集");
                case Region  -> showRegionFeatures(((RegionDataset) dataset).getFeatures(), dataset.getName(), "面数据集");
                case RegionZ -> showRegionFeatures(((RegionZDataset) dataset).getFeatures(), dataset.getName(), "3D 面数据集");
                case CAD     -> showCadFeatures((CadDataset) dataset);
                case Tabular -> showTabularRecords((TabularDataset) dataset);
                default -> {
                    clearTable();
                    setStatus("暂不支持展示该类型数据集：" + info.datasetType());
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "加载数据集出错：\n" + ex.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            setStatus("加载失败：" + info.datasetName());
        }
    }

    private void showPointFeatures(List<PointFeature> features, String name, String typeName) {
        if (features.isEmpty()) {
            showEmpty(name, true);
            return;
        }
        List<String> cols = buildColumns(features.get(0).attributes(), true);
        Object[][] data = new Object[features.size()][cols.size()];
        for (int i = 0; i < features.size(); i++) {
            PointFeature f = features.get(i);
            data[i][0] = f.smId();
            data[i][1] = geometrySummary(f.geometry());
            fillAttrs(data[i], f.attributes(), cols, 2);
        }
        applyTable(data, cols.toArray(String[]::new));
        setStatus(name + "（" + typeName + "，" + features.size() + " 条记录）");
    }

    private void showLineFeatures(List<LineFeature> features, String name, String typeName) {
        if (features.isEmpty()) {
            showEmpty(name, true);
            return;
        }
        List<String> cols = buildColumns(features.get(0).attributes(), true);
        Object[][] data = new Object[features.size()][cols.size()];
        for (int i = 0; i < features.size(); i++) {
            LineFeature f = features.get(i);
            data[i][0] = f.smId();
            data[i][1] = geometrySummary(f.geometry());
            fillAttrs(data[i], f.attributes(), cols, 2);
        }
        applyTable(data, cols.toArray(String[]::new));
        setStatus(name + "（" + typeName + "，" + features.size() + " 条记录）");
    }

    private void showRegionFeatures(List<RegionFeature> features, String name, String typeName) {
        if (features.isEmpty()) {
            showEmpty(name, true);
            return;
        }
        List<String> cols = buildColumns(features.get(0).attributes(), true);
        Object[][] data = new Object[features.size()][cols.size()];
        for (int i = 0; i < features.size(); i++) {
            RegionFeature f = features.get(i);
            data[i][0] = f.smId();
            data[i][1] = geometrySummary(f.geometry());
            fillAttrs(data[i], f.attributes(), cols, 2);
        }
        applyTable(data, cols.toArray(String[]::new));
        setStatus(name + "（" + typeName + "，" + features.size() + " 条记录）");
    }

    private void showCadFeatures(CadDataset dataset) {
        List<CadFeature> features = dataset.getFeatures();
        if (features.isEmpty()) {
            showEmpty(dataset.getName(), true);
            return;
        }
        List<String> cols = buildColumns(features.get(0).attributes(), true);
        Object[][] data = new Object[features.size()][cols.size()];
        for (int i = 0; i < features.size(); i++) {
            CadFeature f = features.get(i);
            data[i][0] = f.smId();
            data[i][1] = f.geometry() == null ? "(null)" : f.geometry().getClass().getSimpleName();
            fillAttrs(data[i], f.attributes(), cols, 2);
        }
        applyTable(data, cols.toArray(String[]::new));
        setStatus(dataset.getName() + "（CAD 数据集，" + features.size() + " 条记录）");
    }

    private void showTabularRecords(TabularDataset dataset) {
        List<TabularRecord> records = dataset.getRecords();
        if (records.isEmpty()) {
            showEmpty(dataset.getName(), false);
            return;
        }
        List<String> cols = buildColumns(records.get(0).attributes(), false);
        Object[][] data = new Object[records.size()][cols.size()];
        for (int i = 0; i < records.size(); i++) {
            TabularRecord r = records.get(i);
            data[i][0] = r.smId();
            fillAttrs(data[i], r.attributes(), cols, 1);
        }
        applyTable(data, cols.toArray(String[]::new));
        setStatus(dataset.getName() + "（属性表，" + records.size() + " 条记录）");
    }

    // ── 表格辅助 ──────────────────────────────────────────────────────────────

    /** 构建列名列表：SmID [+ Geometry] + 属性字段 */
    private List<String> buildColumns(Map<String, Object> attrs, boolean hasGeometry) {
        List<String> cols = new ArrayList<>();
        cols.add("SmID");
        if (hasGeometry) cols.add("Geometry");
        cols.addAll(new LinkedHashSet<>(attrs.keySet()));
        return cols;
    }

    private void fillAttrs(Object[] row, Map<String, Object> attrs, List<String> cols, int startCol) {
        for (int c = startCol; c < cols.size(); c++) {
            row[c] = attrs.get(cols.get(c));
        }
    }

    private void applyTable(Object[][] data, String[] cols) {
        dataTable.setModel(new javax.swing.table.DefaultTableModel(data, cols) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        });
        // 自动调整列宽
        for (int c = 0; c < dataTable.getColumnCount(); c++) {
            int width = 80;
            for (int r = 0; r < Math.min(dataTable.getRowCount(), 50); r++) {
                Object val = dataTable.getValueAt(r, c);
                if (val != null) {
                    int w = dataTable.getFontMetrics(dataTable.getFont())
                            .stringWidth(val.toString()) + 20;
                    width = Math.max(width, w);
                }
            }
            dataTable.getColumnModel().getColumn(c).setPreferredWidth(Math.min(width, 300));
        }
    }

    private void showEmpty(String name, boolean hasGeometry) {
        String[] cols = hasGeometry ? new String[]{"SmID", "Geometry"} : new String[]{"SmID"};
        applyTable(new Object[0][cols.length], cols);
        setStatus(name + "（空数据集，0 条记录）");
    }

    private void clearTable() {
        dataTable.setModel(new javax.swing.table.DefaultTableModel());
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────────

    private String geometrySummary(Object geometry) {
        if (geometry == null) return "(null)";
        String wkt = geometry.toString();
        return wkt.length() > 80 ? wkt.substring(0, 77) + "..." : wkt;
    }

    private void setStatus(String msg) {
        statusBar.setText(" " + msg);
    }

    // ── 内部数据类 ────────────────────────────────────────────────────────────

    /** 树节点携带的元数据 */
    private record DatasetNode(String label, DatasetInfo info) {
        @Override
        public String toString() {
            return label;
        }
    }

    // ── 图标与渲染器 ──────────────────────────────────────────────────────────

    /** 可在查看器中展示数据的类型集合 */
    private static final Set<DatasetType> SUPPORTED_TYPES = EnumSet.of(
            DatasetType.Point, DatasetType.PointZ,
            DatasetType.Line,  DatasetType.LineZ,
            DatasetType.Region, DatasetType.RegionZ,
            DatasetType.CAD,   DatasetType.Tabular);

    /**
     * 自定义树单元格渲染器，为每种数据集类型显示不同图标：
     * <ul>
     *   <li>支持展示的类型：各自的彩色几何图标</li>
     *   <li>不支持展示的类型：灰底警告图标，文字显示为灰色</li>
     * </ul>
     */
    private static class DatasetTreeCellRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (!(value instanceof DefaultMutableTreeNode dmtn)) return this;
            Object userObj = dmtn.getUserObject();
            if (!(userObj instanceof DatasetNode dn)) return this;

            DatasetType type = dn.info().datasetType();
            setIcon(DatasetIcons.forType(type));
            if (!SUPPORTED_TYPES.contains(type)) {
                setForeground(selected ? Color.WHITE : Color.GRAY);
            }
            return this;
        }
    }

    /** 按需创建并缓存各数据集类型的图标 */
    private static class DatasetIcons {

        private static final java.util.Map<DatasetType, Icon> CACHE = new java.util.EnumMap<>(DatasetType.class);

        static Icon forType(DatasetType type) {
            return CACHE.computeIfAbsent(type, DatasetIcons::create);
        }

        private static Icon create(DatasetType type) {
            return switch (type) {
                case Point   -> iconPoint(new Color(0x2196F3), false);  // 蓝色圆点
                case PointZ  -> iconPoint(new Color(0x0D47A1), true);   // 深蓝 3D 点
                case Line    -> iconLine(new Color(0x4CAF50), false);   // 绿色线
                case LineZ   -> iconLine(new Color(0x1B5E20), true);    // 深绿 3D 线
                case Region  -> iconRegion(new Color(0xFF9800), false); // 橙色面
                case RegionZ -> iconRegion(new Color(0xE65100), true);  // 深橙 3D 面
                case CAD     -> iconCad();                              // 紫色 CAD
                case Tabular -> iconTabular();                          // 灰色表格
                default      -> iconUnsupported();                      // 黄色警告
            };
        }

        // 点：实心圆，可选右下角 "Z" 标记
        private static Icon iconPoint(Color color, boolean is3d) {
            return draw(g -> {
                g.setColor(color);
                g.fillOval(3, 3, 10, 10);
                g.setColor(color.darker());
                g.drawOval(3, 3, 10, 10);
                if (is3d) drawZ(g, color.darker().darker());
            });
        }

        // 线：两条对角线，可选 "Z" 标记
        private static Icon iconLine(Color color, boolean is3d) {
            return draw(g -> {
                g.setColor(color);
                g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(2, 13, 8, 3);
                g.drawLine(7, 13, 13, 3);
                if (is3d) drawZ(g, color.darker());
            });
        }

        // 面：实心五边形，可选 "Z" 标记
        private static Icon iconRegion(Color color, boolean is3d) {
            return draw(g -> {
                int[] xs = {8, 13, 11, 5, 3};
                int[] ys = {2,  7, 13, 13,  7};
                g.setColor(color);
                g.fillPolygon(xs, ys, 5);
                g.setColor(color.darker());
                g.setStroke(new BasicStroke(1.0f));
                g.drawPolygon(xs, ys, 5);
                if (is3d) drawZ(g, color.darker().darker());
            });
        }

        // CAD：紫色矩形叠三角形
        private static Icon iconCad() {
            Color c = new Color(0x7B1FA2);
            return draw(g -> {
                g.setColor(c);
                g.fillRect(2, 5, 8, 8);
                int[] xs = {7, 14, 14};
                int[] ys = {3,  3, 10};
                g.fillPolygon(xs, ys, 3);
                g.setColor(c.darker());
                g.setStroke(new BasicStroke(1.0f));
                g.drawRect(2, 5, 8, 8);
                g.drawPolygon(xs, ys, 3);
            });
        }

        // 属性表：三条横线（模拟表格行）
        private static Icon iconTabular() {
            Color c = new Color(0x757575);
            return draw(g -> {
                g.setColor(c);
                g.fillRect(2, 2, 12, 3);
                g.fillRect(2, 7, 12, 2);
                g.fillRect(2, 11, 12, 2);
            });
        }

        // 不支持：黄底警告三角形 + 感叹号
        private static Icon iconUnsupported() {
            return draw(g -> {
                int[] xs = {8, 15, 1};
                int[] ys = {1, 14, 14};
                g.setColor(new Color(0xFFC107));
                g.fillPolygon(xs, ys, 3);
                g.setColor(new Color(0x795548));
                g.setStroke(new BasicStroke(1.0f));
                g.drawPolygon(xs, ys, 3);
                // 感叹号
                g.setColor(new Color(0x5D4037));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
                g.drawString("!", 6, 13);
            });
        }

        /** 右下角绘制小写 "z" 表示 3D */
        private static void drawZ(Graphics2D g, Color color) {
            g.setColor(color);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
            g.drawString("z", 10, 15);
        }

        /** 创建 16×16 的 ImageIcon，传入 lambda 负责绘制 */
        private static Icon draw(java.util.function.Consumer<Graphics2D> painter) {
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            painter.accept(g);
            g.dispose();
            return new ImageIcon(img);
        }
    }
}
