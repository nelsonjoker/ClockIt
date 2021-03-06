package com.kelio.webservice;
//----------------------------------------------------
//
// Generated by www.easywsdl.com
// Version: 5.10.3.0
//
// Created by Quasar Development 
//
//----------------------------------------------------



import java.util.Hashtable;
import org.ksoap2.serialization.*;

public class AskedPopulation extends EmployeeInformation implements KvmSerializable
{

    
    private String groupFilter;
    
    private Integer populationMode;
    
    private String populationFilter;
    
    private java.util.Date populationEndDate;
    
    private java.util.Date populationStartDate;
    
    public String getGroupFilter()
    {
        return this.groupFilter;
    }
    
    public void setGroupFilter(String value)
    {
        this.groupFilter = value;     
    }
    
    public Integer getPopulationMode()
    {
        return this.populationMode;
    }
    
    public void setPopulationMode(Integer value)
    {
        this.populationMode = value;     
    }
    
    public String getPopulationFilter()
    {
        return this.populationFilter;
    }
    
    public void setPopulationFilter(String value)
    {
        this.populationFilter = value;     
    }
    
    public java.util.Date getPopulationEndDate()
    {
        return this.populationEndDate;
    }
    
    public void setPopulationEndDate(java.util.Date value)
    {
        this.populationEndDate = value;     
    }
    
    public java.util.Date getPopulationStartDate()
    {
        return this.populationStartDate;
    }
    
    public void setPopulationStartDate(java.util.Date value)
    {
        this.populationStartDate = value;     
    }


    
    
    @Override
    public void loadFromSoap(java.lang.Object paramObj, ExtendedSoapSerializationEnvelope __envelope)
    {
        if (paramObj == null)
            return;
        AttributeContainer inObj=(AttributeContainer)paramObj;
        super.loadFromSoap(paramObj, __envelope);



    }

    @Override
    protected boolean loadProperty(PropertyInfo info, SoapObject soapObject, ExtendedSoapSerializationEnvelope __envelope)
    {
        java.lang.Object obj = info.getValue();
        if (info.name.equals("groupFilter"))
        {
            if(obj!=null)
            {
                if (obj instanceof SoapPrimitive)
                {
                    SoapPrimitive j =(SoapPrimitive) obj;
                    if(j.toString()!=null)
                    {
                        this.groupFilter = j.toString();
                    }
                }
                else if (obj instanceof String){
                    this.groupFilter = (String)obj;
                }
                else{
                    this.groupFilter = "";
                }
            }
            return true;
        }
        if (info.name.equals("populationMode"))
        {
            if(obj!=null)
            {
                if (obj instanceof SoapPrimitive)
                {
                    SoapPrimitive j =(SoapPrimitive) obj;
                    if(j.toString()!=null)
                    {
                        this.populationMode = Integer.parseInt(j.toString());
                    }
                }
                else if (obj instanceof Integer){
                    this.populationMode = (Integer)obj;
                }
            }
            return true;
        }
        if (info.name.equals("populationFilter"))
        {
            if(obj!=null)
            {
                if (obj instanceof SoapPrimitive)
                {
                    SoapPrimitive j =(SoapPrimitive) obj;
                    if(j.toString()!=null)
                    {
                        this.populationFilter = j.toString();
                    }
                }
                else if (obj instanceof String){
                    this.populationFilter = (String)obj;
                }
                else{
                    this.populationFilter = "";
                }
            }
            return true;
        }
        if (info.name.equals("populationEndDate"))
        {
            if(obj!=null)
            {
                if (obj instanceof SoapPrimitive)
                {
                    SoapPrimitive j =(SoapPrimitive) obj;
                    if(j.toString()!=null)
                    {
                        this.populationEndDate = ExtendedSoapSerializationEnvelope.getDateTimeConverter().convertDateTime(j.toString());
                    }
                }
                else if (obj instanceof java.util.Date){
                    this.populationEndDate = (java.util.Date)obj;
                }
            }
            return true;
        }
        if (info.name.equals("populationStartDate"))
        {
            if(obj!=null)
            {
                if (obj instanceof SoapPrimitive)
                {
                    SoapPrimitive j =(SoapPrimitive) obj;
                    if(j.toString()!=null)
                    {
                        this.populationStartDate = ExtendedSoapSerializationEnvelope.getDateTimeConverter().convertDateTime(j.toString());
                    }
                }
                else if (obj instanceof java.util.Date){
                    this.populationStartDate = (java.util.Date)obj;
                }
            }
            return true;
        }
        return super.loadProperty(info,soapObject,__envelope);
    }
    
    

    @Override
    public java.lang.Object getProperty(int propertyIndex) {
        int count = super.getPropertyCount();
        //!!!!! If you have a compilation error here then you are using old version of ksoap2 library. Please upgrade to the latest version.
        //!!!!! You can find a correct version in Lib folder from generated zip file!!!!!
        if(propertyIndex==count+0)
        {
            return this.groupFilter!=null?this.groupFilter:SoapPrimitive.NullSkip;
        }
        else if(propertyIndex==count+1)
        {
            return this.populationMode!=null?this.populationMode:SoapPrimitive.NullSkip;
        }
        else if(propertyIndex==count+2)
        {
            return this.populationFilter!=null?this.populationFilter:SoapPrimitive.NullSkip;
        }
        else if(propertyIndex==count+3)
        {
            return this.populationEndDate!=null? ExtendedSoapSerializationEnvelope.getDateTimeConverter().getStringFromDate(this.populationEndDate):SoapPrimitive.NullSkip;
        }
        else if(propertyIndex==count+4)
        {
            return this.populationStartDate!=null? ExtendedSoapSerializationEnvelope.getDateTimeConverter().getStringFromDate(this.populationStartDate):SoapPrimitive.NullSkip;
        }
        return super.getProperty(propertyIndex);
    }


    @Override
    public int getPropertyCount() {
        return super.getPropertyCount()+5;
    }

    @Override
    public void getPropertyInfo(int propertyIndex, Hashtable arg1, PropertyInfo info)
    {
        int count = super.getPropertyCount();
        if(propertyIndex==count+0)
        {
            info.type = PropertyInfo.STRING_CLASS;
            info.name = "groupFilter";
            info.namespace= "http://echange.service.open.bodet.com";
        }
        else if(propertyIndex==count+1)
        {
            info.type = PropertyInfo.INTEGER_CLASS;
            info.name = "populationMode";
            info.namespace= "http://echange.service.open.bodet.com";
        }
        else if(propertyIndex==count+2)
        {
            info.type = PropertyInfo.STRING_CLASS;
            info.name = "populationFilter";
            info.namespace= "http://echange.service.open.bodet.com";
        }
        else if(propertyIndex==count+3)
        {
            info.type = PropertyInfo.STRING_CLASS;
            info.name = "populationEndDate";
            info.namespace= "http://echange.service.open.bodet.com";
        }
        else if(propertyIndex==count+4)
        {
            info.type = PropertyInfo.STRING_CLASS;
            info.name = "populationStartDate";
            info.namespace= "http://echange.service.open.bodet.com";
        }
        super.getPropertyInfo(propertyIndex,arg1,info);
    }

    @Override
    public void setProperty(int arg0, java.lang.Object arg1)
    {
    }

    
}
