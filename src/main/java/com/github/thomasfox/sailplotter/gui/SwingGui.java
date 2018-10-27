package com.github.thomasfox.sailplotter.gui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ListSelectionEvent;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.PolarPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.statistics.SimpleHistogramBin;
import org.jfree.data.statistics.SimpleHistogramDataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.github.thomasfox.sailplotter.Constants;
import com.github.thomasfox.sailplotter.analyze.DeviceOrientationAnalyzer;
import com.github.thomasfox.sailplotter.analyze.LocationInterpolator;
import com.github.thomasfox.sailplotter.analyze.TackListByCorrelationAnalyzer;
import com.github.thomasfox.sailplotter.analyze.TackSeriesAnalyzer;
import com.github.thomasfox.sailplotter.analyze.UseGpsTimeDataCorrector;
import com.github.thomasfox.sailplotter.analyze.VelocityBearingAnalyzer;
import com.github.thomasfox.sailplotter.exporter.Exporter;
import com.github.thomasfox.sailplotter.gui.plot.AbstractPlotPanel;
import com.github.thomasfox.sailplotter.gui.plot.FullMapPlotPanel;
import com.github.thomasfox.sailplotter.gui.plot.FullVelocityBearingOverTimePlotPanel;
import com.github.thomasfox.sailplotter.gui.plot.VelocityBearingPolarPlotPanel;
import com.github.thomasfox.sailplotter.gui.plot.ZoomedMapPlotPanel;
import com.github.thomasfox.sailplotter.gui.plot.ZoomedVelocityBearingOverTimePlotPanel;
import com.github.thomasfox.sailplotter.importer.FormatAwareImporter;
import com.github.thomasfox.sailplotter.model.Data;
import com.github.thomasfox.sailplotter.model.DataPoint;
import com.github.thomasfox.sailplotter.model.Tack;
import com.github.thomasfox.sailplotter.model.TackSeries;

public class SwingGui
{
  private static final String OVERVIEW_VIEW_NAME = "Overview";

  private static final String DIRECTIONS_VIEW_NAME = "Directions";

  private static final String COMMENTS_VIEW_NAME = "Comments";

  private final JFrame frame;

  private final ZoomPanel zoomPanel;

  private final Menubar menubar;

  private final AbstractPlotPanel fullVelocityBearingOverTimePlotPanel;

  private final AbstractPlotPanel zoomedVelocityBearingOverTimePlotPanel;

  private final AbstractPlotPanel fullMapPlotPanel;

  private final AbstractPlotPanel zoomedMapPlotPanel;

  private final AbstractPlotPanel velocityBearingPolarPlotPanel;

  SimpleHistogramDataset bearingHistogramDataset;

  List<SimpleHistogramBin> bearingHistogramBins = new ArrayList<>();

  XYSeriesCollection tackVelocityBearingPolar = new XYSeriesCollection();

  Data data;

  List<DataPoint> pointsWithLocation;

  List<TackSeries> tackSeriesList;

  TackTablePanel tackTablePanel;

  TackSeriesTablePanel tackSeriesTablePanel;

  JPanel views;

  TimeSeriesCollection zoomedBearingOverTimeDataset = new TimeSeriesCollection();

  CommentPanel commentPanel;

  double windBearing;

  boolean inUpdate = false;

  public SwingGui(String filePath, int windDirectionInDegrees)
  {
    this.windBearing = 2 * Math.PI * windDirectionInDegrees / 360d;
    data = new FormatAwareImporter().read(new File(filePath));
    analyze();
    zoomPanel = new ZoomPanel(pointsWithLocation.size());

    MainPanel overview = new MainPanel();
    MainPanel directions = new MainPanel();
    MainPanel comments = new MainPanel();

    views = new JPanel(new CardLayout());
    views.add(overview, OVERVIEW_VIEW_NAME);
    views.add(directions, DIRECTIONS_VIEW_NAME);
    views.add(comments, COMMENTS_VIEW_NAME);

    frame = new JFrame("SailPlotter");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    menubar = new Menubar(frame)
        .addLoadFileMenuItem(new File(filePath), this::loadFile)
        .addSaveFileMenuItem(new Exporter().replaceExtension(new File(filePath)), this::saveFile)
        .addViews(this::changeView,
            OVERVIEW_VIEW_NAME,
            DIRECTIONS_VIEW_NAME,
            COMMENTS_VIEW_NAME);
    frame.setJMenuBar(menubar);

    frame.getContentPane().add(views, BorderLayout.CENTER);

    fullVelocityBearingOverTimePlotPanel = new FullVelocityBearingOverTimePlotPanel(data, zoomPanel.getStartIndex(), zoomPanel.getZoomIndex());
    overview.layoutForAdding().gridx(0).gridy(0).weightx(0.333).weighty(0.25)
        .add(fullVelocityBearingOverTimePlotPanel);

    zoomedVelocityBearingOverTimePlotPanel = new ZoomedVelocityBearingOverTimePlotPanel(data, zoomPanel.getStartIndex(), zoomPanel.getZoomIndex());
    overview.layoutForAdding().gridx(1).gridy(0).weightx(0.333).weighty(0.25)
        .add(zoomedVelocityBearingOverTimePlotPanel);

    JPanel topRightPanel = new JPanel();
    bearingHistogramDataset = new SimpleHistogramDataset("Relative Bearing");
    bearingHistogramDataset.setAdjustForBinSize(false);
    for (int i = 0; i < Constants.NUMBER_OF_BEARING_BINS; ++i)
    {
      SimpleHistogramBin bin = new SimpleHistogramBin(
          (i * 360d / Constants.NUMBER_OF_BEARING_BINS) - 180d,
          ((i + 1) * 360d / Constants.NUMBER_OF_BEARING_BINS) - 180d,
          true,
          false);
      bearingHistogramBins.add(bin);
      bearingHistogramDataset.addBin(bin);
    }
    updateBearingHistogramDataset();
    JFreeChart bearingHistogramChart = ChartFactory.createHistogram("Relative Bearing", "Relative Bearing [�]", "Occurances",  bearingHistogramDataset, PlotOrientation.VERTICAL, false, false, false);
    ChartPanel bearingChartPanel = new ChartPanel(bearingHistogramChart);
    topRightPanel.add(bearingChartPanel);

    zoomPanel.addListener(this::zoomPanelStateChanged);
    topRightPanel.add(zoomPanel);

    JPanel windDirectionPanel = new JPanel();
    JLabel windDirectionLabel = new JLabel("Wind direction");
    windDirectionPanel.add(windDirectionLabel);
    JTextField windDirectionTextField = new JTextField();
    Dimension windDirectionTextFieldSize = windDirectionTextField.getPreferredSize();
    windDirectionTextFieldSize.width=30;
    windDirectionTextField.setPreferredSize(windDirectionTextFieldSize);
    windDirectionTextField.setText(Integer.toString(windDirectionInDegrees));
    windDirectionTextField.addActionListener(this::windDirectionChanged);
    windDirectionPanel.add(windDirectionTextField);
    topRightPanel.add(windDirectionPanel);

    topRightPanel.setLayout(new BoxLayout(topRightPanel, BoxLayout.PAGE_AXIS));
    overview.layoutForAdding().gridx(2).gridy(0).weightx(0.333).weighty(0.25).columnSpan(2)
        .add(topRightPanel);

    fullMapPlotPanel = new FullMapPlotPanel(data, zoomPanel.getStartIndex(), zoomPanel.getZoomIndex());
    overview.layoutForAdding().gridx(0).gridy(1).weightx(0.333).weighty(0.5)
        .add(fullMapPlotPanel);

    zoomedMapPlotPanel = new ZoomedMapPlotPanel(data, zoomPanel.getStartIndex(), zoomPanel.getZoomIndex());
    overview.layoutForAdding().gridx(1).gridy(1).weightx(0.333).weighty(0.5)
        .add(zoomedMapPlotPanel);

    updateTackVelocityBearingPolar();
    JFreeChart tackVelocityBearingChart = ChartFactory.createPolarChart("Tack Velocity over rel. Bearing", tackVelocityBearingPolar, false, true, false);
    PolarPlot tackVelocityBearingPlot = (PolarPlot) tackVelocityBearingChart.getPlot();
    PolarScatterRenderer tackVelocityRenderer = new PolarScatterRenderer();
    tackVelocityRenderer.setBaseToolTipGenerator(new XYTooltipFromLabelGenerator());
    tackVelocityBearingPlot.setRenderer(tackVelocityRenderer);

    ChartPanel tackVelocityBearingChartPanel = new ChartPanel(tackVelocityBearingChart);
    overview.layoutForAdding().gridx(2).gridy(1).weightx(0.166).weighty(0.5)
        .add(tackVelocityBearingChartPanel);

    velocityBearingPolarPlotPanel = new VelocityBearingPolarPlotPanel(data, zoomPanel.getStartIndex(), zoomPanel.getZoomIndex());
    overview.layoutForAdding().gridx(3).gridy(1).weightx(0.166).weighty(0.5)
        .add(velocityBearingPolarPlotPanel);

    tackTablePanel = new TackTablePanel(data.getTackList(), this::tackSelected);
    overview.layoutForAdding().gridx(0).gridy(2).weightx(0.666).weighty(0.25).columnSpan(2)
        .add(tackTablePanel);

    tackSeriesTablePanel = new TackSeriesTablePanel(tackSeriesList, this::tackSeriesSelected);
    overview.layoutForAdding().gridx(2).gridy(2).weightx(0.666).weighty(0.25).columnSpan(2)
        .add(tackSeriesTablePanel);

    updateZoomedBearingOverTimeDataset();
    JFreeChart zoomedBearingOverTimeChart = ChartFactory.createTimeSeriesChart("Bearing (Zoom)", "Time", "Bearing [arcs]", zoomedBearingOverTimeDataset, true, false, false);
    XYPlot zoomedBearingOverTimePlot = (XYPlot) zoomedBearingOverTimeChart.getPlot();
    zoomedBearingOverTimePlot.getRenderer().setSeriesPaint(0, new Color(0xFF, 0x00, 0x00));
    zoomedBearingOverTimePlot.getRenderer().setSeriesPaint(1, new Color(0x00, 0xFF, 0x00));
    zoomedBearingOverTimePlot.getRenderer().setSeriesPaint(2, new Color(0x00, 0x00, 0xFF));
    ((XYLineAndShapeRenderer) zoomedBearingOverTimePlot.getRenderer()).setSeriesShapesVisible(0, true);
    ((XYLineAndShapeRenderer) zoomedBearingOverTimePlot.getRenderer()).setSeriesShapesVisible(1, true);
    ((XYLineAndShapeRenderer) zoomedBearingOverTimePlot.getRenderer()).setSeriesShapesVisible(2, true);
    ChartPanel zoomedBearingOverTimeChartPanel = new ChartPanel(zoomedBearingOverTimeChart);
    directions.layoutForAdding().gridx(0).gridy(0).weightx(0.5).weighty(0.5)
        .add(zoomedBearingOverTimeChartPanel);

    commentPanel = new CommentPanel(data.comment, data::setComment);
    comments.layoutForAdding().gridx(0).gridy(0).weightx(1).weighty(0.9)
      .add(commentPanel);

    frame.pack();
    frame.setVisible(true);
  }

  public static void main(String[] args)
  {
    if (args.length != 2)
    {
      printUsage();
      return;
    }
    String filename = args[0];
    File file;
    try
    {
      file = new File(filename);
    }
    catch (Exception e)
    {
      printUsage();
      return;
    }
    if (!file.canRead())
    {
      System.out.println("File " + filename + " cannot be read");
      return;
    }
    int windDirectionInDegreees;
    try
    {
      windDirectionInDegreees = Integer.parseInt(args[1]);
    }
    catch (Exception e)
    {
      printUsage();
      return;
    }
    javax.swing.SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run() {
        new SwingGui(filename, windDirectionInDegreees);
      }
    });
  }

  private static void printUsage()
  {
    System.out.println("Usage: ${startcommand} ${file} ${windDirectionInDegreees}");
  }

  private void updateZoomedBearingOverTimeDataset()
  {
    zoomedBearingOverTimeDataset.removeAllSeries();
    zoomedBearingOverTimeDataset.addSeries(getBearingFromLatLongTimeSeries(TimeWindowPosition.IN));
    zoomedBearingOverTimeDataset.addSeries(getGpsBearingTimeSeries(TimeWindowPosition.IN));
    zoomedBearingOverTimeDataset.addSeries(getCompassBearingTimeSeries(TimeWindowPosition.IN));
  }

  public static TimeSeries getLatitudeTimeSeries(List<DataPoint> data)
  {
    TimeSeries series = new TimeSeries("latitude");
    for (DataPoint point: data)
    {
      series.addOrUpdate(point.getMillisecond(), point.location.latitude);
    }
    return series;
  }

  public TimeSeries getBearingFromLatLongTimeSeries(TimeWindowPosition position)
  {
    TimeSeries series = new TimeSeries("bearing");
    for (DataPoint point : getLocationSubset(position))
    {
      series.addOrUpdate(point.getMillisecond(), point.location.bearingFromLatLong);
    }
    return series;
  }

  public TimeSeries getGpsBearingTimeSeries(TimeWindowPosition position)
  {
    TimeSeries series = new TimeSeries("gps bearing");
    for (DataPoint point : getLocationSubset(position))
    {
      series.addOrUpdate(point.getMillisecond(), point.location.bearing);
    }
    return series;
  }

  public TimeSeries getCompassBearingTimeSeries(TimeWindowPosition position)
  {
    TimeSeries series = new TimeSeries("compass bearing");
    for (DataPoint point : data.getAllPoints())
    {
      if (isInSelectedPosition(point, position) && point.hasMagneticField() && point.magneticField.compassBearing != null)
      {
        series.addOrUpdate(point.getMillisecond(), point.magneticField.compassBearing);
      }
    }
    return series;
  }

  public TimeSeries getZoomDisplaySeries(List<DataPoint> data)
  {
    Millisecond startValue = data.get(getLocationDataStartIndex()).getMillisecond();
    Millisecond endValue = data.get(getLocationDataEndIndex()).getMillisecond();
    TimeSeries series = new TimeSeries("velocity");
    series.addOrUpdate(startValue, 2);
    series.addOrUpdate(endValue, 2);
    return series;
  }

  public int getLocationDataStartIndex()
  {
    return zoomPanel.getStartIndex();
  }

  public int getLocationDataEndIndex()
  {
    int zoom = zoomPanel.getZoomIndex();
    int startIndex = getLocationDataStartIndex();
    int result = startIndex + zoom * (pointsWithLocation.size() - 1) / Constants.NUMER_OF_ZOOM_TICKS;
    result = Math.min(result, (pointsWithLocation.size() - 1));
    return result;
  }

  public LocalDateTime getLocationDataStartTime()
  {
    return pointsWithLocation.get(getLocationDataStartIndex()).getLocalDateTime();
  }

  public LocalDateTime getLocationDataEndTime()
  {
    return pointsWithLocation.get(getLocationDataEndIndex()).getLocalDateTime();
  }

  public void updateBearingHistogramDataset()
  {
    for (SimpleHistogramBin bin : bearingHistogramBins)
    {
      bin.setItemCount(0);
    }
    for (DataPoint point : pointsWithLocation)
    {
      if (point.getLocalDateTime().isAfter(getLocationDataStartTime())
          && point.getLocalDateTime().isBefore(getLocationDataEndTime()))
      {
        if (point.location.bearingFromLatLong != null)
        {
          bearingHistogramDataset.addObservation(point.getRelativeBearingAs360Degrees());
        }
      }
    }
  }

  public void updateTackVelocityBearingPolar()
  {
    XYSeries tackVelocity = new XYSeries("tackVelocity", false, true);
    for (Tack tack : data.getTackList())
    {
      if (tack.end.getLocalDateTime().isAfter(getLocationDataStartTime())
          && tack.start.getLocalDateTime().isBefore(getLocationDataEndTime())
          && tack.hasMainPoints())
      {
        if (tack.getRelativeBearingInDegrees() != null && tack.getVelocityInKnots() != null)
        {
          tackVelocity.add(new XYSailDataItem(
              tack.getRelativeBearingInDegrees(),
              tack.getVelocityInKnots(),
              tack.getLabel()));
        }
      }
    }
    tackVelocityBearingPolar.removeAllSeries();
    tackVelocityBearingPolar.addSeries(tackVelocity);
  }

  private boolean isInSelectedPosition(DataPoint point, TimeWindowPosition position)
  {
    if (position == TimeWindowPosition.BEFORE && point.getLocalDateTime().isAfter(getLocationDataStartTime()))
    {
      return false;
    }
    if (position == TimeWindowPosition.IN
        && (!point.getLocalDateTime().isAfter(getLocationDataStartTime())
            || !point.getLocalDateTime().isBefore(getLocationDataEndTime())))
    {
      return false;
    }
    if (position == TimeWindowPosition.AFTER && point.getLocalDateTime().isBefore(getLocationDataEndTime()))
    {
      return false;
    }
    return true;
  }


  List<DataPoint> getLocationSubset(TimeWindowPosition position)
  {
    List<DataPoint> result = new ArrayList<>();
    for (DataPoint point : pointsWithLocation)
    {
      if (!isInSelectedPosition(point, position))
      {
        continue;
      }

      result.add(point);
    }
    return result;
  }


  public void analyze()
  {
    pointsWithLocation = data.getPointsWithLocation();
    new UseGpsTimeDataCorrector().correct(data);
    new LocationInterpolator().interpolateLocation(data);
    new VelocityBearingAnalyzer().analyze(data, windBearing);
    data.getTackList().clear();
    data.getTackList().addAll(new TackListByCorrelationAnalyzer().analyze(data));
    tackSeriesList = new TackSeriesAnalyzer().analyze(data.getTackList());
    new DeviceOrientationAnalyzer().analyze(data);
  }

  public void redisplay(boolean updateTableContent)
  {
    try
    {
      inUpdate = true;
      int zoomWindowStartIndex = zoomPanel.getStartIndex();
      int zoomWindowZoomIndex = zoomPanel.getZoomIndex();
      fullVelocityBearingOverTimePlotPanel.zoomChanged(zoomWindowStartIndex, zoomWindowZoomIndex);
      zoomedVelocityBearingOverTimePlotPanel.zoomChanged(zoomWindowStartIndex, zoomWindowZoomIndex);
      fullMapPlotPanel.zoomChanged(zoomWindowStartIndex, zoomWindowZoomIndex);
      zoomedMapPlotPanel.zoomChanged(zoomWindowStartIndex, zoomWindowZoomIndex);
      updateBearingHistogramDataset();
      velocityBearingPolarPlotPanel.zoomChanged(zoomWindowStartIndex, zoomWindowZoomIndex);
      updateTackVelocityBearingPolar();
      updateZoomedBearingOverTimeDataset();
      commentPanel.setText(data.comment);
      if (updateTableContent)
      {
        tackTablePanel.updateContent(data.getTackList());
        tackSeriesTablePanel.updateContent(tackSeriesList);
      }
    }
    finally
    {
      inUpdate = false;
    }
  }

  public void zoomPanelStateChanged(ZoomPanelChangeEvent e)
  {
    redisplay(false);
  }

  public void tackSelected(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting() || inUpdate)
    {
      return;
    }
    int index = tackTablePanel.getSelectedTackIndex();
    Tack tack = data.getTackList().get(index);
    zoomPanel.setStartIndex(Math.max(tack.startOfTackDataPointIndex - Constants.NUM_DATAPOINTS_TACK_EXTENSION, 0));
    zoomPanel.setZoomIndex(Math.min(
        Math.max(
            Constants.NUMER_OF_ZOOM_TICKS * (tack.endOfTackDataPointIndex - tack.startOfTackDataPointIndex + 2 * Constants.NUM_DATAPOINTS_TACK_EXTENSION) / (pointsWithLocation.size()),
            3),
        Constants.NUMER_OF_ZOOM_TICKS));
  }

  public void windDirectionChanged(ActionEvent event)
  {
    String inputValue = event.getActionCommand();
    try
    {
      int newWindDirection = Integer.parseInt(inputValue);
      this.windBearing = newWindDirection * Math.PI / 180d;
      dataChanged();
      redisplay(true);
    }
    catch (Exception e)
    {
      System.err.println("Could not update wind direction");
      e.printStackTrace(System.err);
    }
  }

  public void tackSeriesSelected(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting() || inUpdate)
    {
      return;
    }
    int index = tackSeriesTablePanel.getSelectedTackSeriesIndex();
    TackSeries tackSeries = tackSeriesList.get(index);
    try
    {
      inUpdate = true;
      tackTablePanel.selectInterval(tackSeries.startTackIndex, tackSeries.endTackIndex);
      zoomPanel.setStartIndex(Math.max(data.getTackList().get(tackSeries.startTackIndex).startOfTackDataPointIndex - Constants.NUM_DATAPOINTS_TACK_EXTENSION, 0));
      zoomPanel.setZoomIndex(Math.min(
          Math.max(
              Constants.NUMER_OF_ZOOM_TICKS * (data.getTackList().get(tackSeries.endTackIndex).endOfTackDataPointIndex - data.getTackList().get(tackSeries.startTackIndex).startOfTackDataPointIndex + 2 * Constants.NUM_DATAPOINTS_TACK_EXTENSION) / (pointsWithLocation.size()),
              3),
          Constants.NUMER_OF_ZOOM_TICKS));
    }
    finally
    {
      inUpdate = false;
    }
    redisplay(false);
  }

  public void loadFile(File file)
  {
    try
    {
      data = new FormatAwareImporter().read(file);
      menubar.setLoadStartFile(file);
      menubar.setSaveStartFile(new Exporter().replaceExtension(file));
      dataChanged();
      redisplay(true);
    }
    catch (Exception e)
    {
      e.printStackTrace();
      JOptionPane.showMessageDialog(
          frame,
          "Could not load File: " + e.getClass().getName() + ":" + e.getMessage(),
          "Error loading File",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  public void dataChanged()
  {
    analyze();
    zoomPanel.setDataSize(pointsWithLocation.size());
    fullVelocityBearingOverTimePlotPanel.dataChanged(data);
    zoomedVelocityBearingOverTimePlotPanel.dataChanged(data);
    fullMapPlotPanel.dataChanged(data);
    zoomedMapPlotPanel.dataChanged(data);
    velocityBearingPolarPlotPanel.dataChanged(data);
  }

  public void saveFile(File file)
  {
    try
    {
      if (file.exists()) {
        JOptionPane.showMessageDialog(
            frame,
            "File exists" ,
            "Error saving File",
            JOptionPane.ERROR_MESSAGE);
      }
      else
      {
        new Exporter().save(file, data);
        JOptionPane.showMessageDialog(
            frame,
            "File saved: " + file.getName() ,
            "File saved",
            JOptionPane.INFORMATION_MESSAGE);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      JOptionPane.showMessageDialog(
          frame,
          "Could not save File: " + e.getClass().getName() + ":" + e.getMessage(),
          "Error saving File",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  public void changeView(String viewName)
  {
    CardLayout cl = (CardLayout)(views.getLayout());
    cl.show(views, viewName);
  }
}