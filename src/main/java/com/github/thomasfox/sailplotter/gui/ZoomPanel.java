package com.github.thomasfox.sailplotter.gui;

import java.awt.Dimension;
import java.awt.Label;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.github.thomasfox.sailplotter.Constants;

public class ZoomPanel extends JPanel implements ChangeListener
{
  private static final long serialVersionUID = 1L;

  private final JSlider startSlider;

  private final JSlider zoomSlider;

  private final List<ZoomPanelChangeListener> listeners = new ArrayList<>();

  public ZoomPanel(int dataSize)
  {
    this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
    setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
    Label label = new Label("Start time");
    label.setAlignment(Label.CENTER);
    label.setPreferredSize(new Dimension(0, 15));
    this.add(label);

    startSlider = new JSlider(JSlider.HORIZONTAL, 0, dataSize- 1, 0);
    startSlider.addChangeListener(this);
    this.add(startSlider);

    label = new Label("Zoom");
    label.setAlignment(Label.CENTER);
    this.add(label);

    zoomSlider = new JSlider(JSlider.HORIZONTAL, 0, Constants.NUMER_OF_ZOOM_TICKS, Constants.NUMER_OF_ZOOM_TICKS);
    zoomSlider.addChangeListener(this);
    this.add(zoomSlider);
  }

  public void setDataSize(int dataSize)
  {
    if (startSlider.getMaximum() != dataSize - 1)
    {
      startSlider.setValue(0);
      startSlider.setMaximum(dataSize- 1);
    }
  }

  @Override
  public void stateChanged(ChangeEvent e)
  {
    notifyListeners();
  }

  public void addListener(ZoomPanelChangeListener listener)
  {
    listeners.add(listener);
  }

  public void removeListener(ZoomPanelChangeListener listener)
  {
    listeners.remove(listener);
  }

  public void clearListeners()
  {
    listeners.clear();
  }

  public void notifyListeners()
  {
    ZoomPanelChangeEvent event = new ZoomPanelChangeEvent(startSlider.getValue(), zoomSlider.getValue());
    listeners.stream().forEach(l -> l.stateChanged(event));
  }

  public int getStartIndex()
  {
    return startSlider.getValue();
  }

  public void setStartIndex(int startIndex)
  {
    startSlider.setValue(startIndex);
  }

  public int getZoomIndex()
  {
    return zoomSlider.getValue();
  }

  public void setZoomIndex(int zoomIndex)
  {
    zoomSlider.setValue(zoomIndex);
  }
}
