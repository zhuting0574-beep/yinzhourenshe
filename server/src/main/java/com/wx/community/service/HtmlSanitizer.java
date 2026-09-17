package com.wx.community.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer {
 private final Safelist safelist=Safelist.relaxed()
  .addTags("h1","h2","h3","h4","h5","h6","span","div","s","u")
  .addAttributes("img","width","height")
  .addProtocols("img","src","http","https").addProtocols("a","href","http","https");

 public String clean(Object value){
  if(value==null)return null;
  String html=String.valueOf(value).trim();
  if(html.isEmpty())return "";
  Document.OutputSettings output=new Document.OutputSettings().prettyPrint(false);
  String cleaned=Jsoup.clean(html,"",safelist,output);
  Document document=Jsoup.parseBodyFragment(cleaned);
  for(Element image:document.select("img"))image.attr("style","max-width:100%;height:auto;display:block;");
  return document.body().html();
 }
}
