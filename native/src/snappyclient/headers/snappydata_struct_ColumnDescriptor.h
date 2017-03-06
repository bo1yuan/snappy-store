/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */

#ifndef SNAPPYDATA_STRUCT_COLUMNDESCRIPTOR_H
#define SNAPPYDATA_STRUCT_COLUMNDESCRIPTOR_H


#include "snappydata_struct_Decimal.h"
#include "snappydata_struct_BlobChunk.h"
#include "snappydata_struct_ClobChunk.h"
#include "snappydata_struct_TransactionXid.h"
#include "snappydata_struct_ServiceMetaData.h"
#include "snappydata_struct_ServiceMetaDataArgs.h"
#include "snappydata_struct_OpenConnectionArgs.h"
#include "snappydata_struct_ConnectionProperties.h"
#include "snappydata_struct_HostAddress.h"
#include "snappydata_struct_SnappyExceptionData.h"
#include "snappydata_struct_StatementAttrs.h"
#include "snappydata_struct_ColumnValue.h"

#include "snappydata_types.h"

namespace io { namespace snappydata { namespace thrift {

typedef struct _ColumnDescriptor__isset {
  _ColumnDescriptor__isset() : scale(false), name(false), fullTableName(false), updatable(false), definitelyUpdatable(false), nullable(false), autoIncrement(false), parameterIn(false), parameterOut(false), elementTypes(false), udtTypeAndClassName(false) {}
  bool scale :1;
  bool name :1;
  bool fullTableName :1;
  bool updatable :1;
  bool definitelyUpdatable :1;
  bool nullable :1;
  bool autoIncrement :1;
  bool parameterIn :1;
  bool parameterOut :1;
  bool elementTypes :1;
  bool udtTypeAndClassName :1;
} _ColumnDescriptor__isset;

class ColumnDescriptor {
 public:

  ColumnDescriptor(const ColumnDescriptor&);
  ColumnDescriptor(ColumnDescriptor&&) noexcept;
  ColumnDescriptor& operator=(const ColumnDescriptor&);
  ColumnDescriptor& operator=(ColumnDescriptor&&) noexcept;
  ColumnDescriptor() : type((SnappyType::type)0), precision(0), scale(0), name(), fullTableName(), updatable(0), definitelyUpdatable(0), nullable(0), autoIncrement(0), parameterIn(0), parameterOut(0), udtTypeAndClassName() {
  }

  virtual ~ColumnDescriptor() noexcept;
  SnappyType::type type;
  int16_t precision;
  int16_t scale;
  std::string name;
  std::string fullTableName;
  bool updatable;
  bool definitelyUpdatable;
  bool nullable;
  bool autoIncrement;
  bool parameterIn;
  bool parameterOut;
  std::vector<SnappyType::type>  elementTypes;
  std::string udtTypeAndClassName;

  _ColumnDescriptor__isset __isset;

  void __set_type(const SnappyType::type val);

  void __set_precision(const int16_t val);

  void __set_scale(const int16_t val);

  void __set_name(const std::string& val);

  void __set_fullTableName(const std::string& val);

  void __set_updatable(const bool val);

  void __set_definitelyUpdatable(const bool val);

  void __set_nullable(const bool val);

  void __set_autoIncrement(const bool val);

  void __set_parameterIn(const bool val);

  void __set_parameterOut(const bool val);

  void __set_elementTypes(const std::vector<SnappyType::type> & val);

  void __set_udtTypeAndClassName(const std::string& val);

  bool operator == (const ColumnDescriptor & rhs) const
  {
    if (!(type == rhs.type))
      return false;
    if (!(precision == rhs.precision))
      return false;
    if (__isset.scale != rhs.__isset.scale)
      return false;
    else if (__isset.scale && !(scale == rhs.scale))
      return false;
    if (__isset.name != rhs.__isset.name)
      return false;
    else if (__isset.name && !(name == rhs.name))
      return false;
    if (__isset.fullTableName != rhs.__isset.fullTableName)
      return false;
    else if (__isset.fullTableName && !(fullTableName == rhs.fullTableName))
      return false;
    if (__isset.updatable != rhs.__isset.updatable)
      return false;
    else if (__isset.updatable && !(updatable == rhs.updatable))
      return false;
    if (__isset.definitelyUpdatable != rhs.__isset.definitelyUpdatable)
      return false;
    else if (__isset.definitelyUpdatable && !(definitelyUpdatable == rhs.definitelyUpdatable))
      return false;
    if (__isset.nullable != rhs.__isset.nullable)
      return false;
    else if (__isset.nullable && !(nullable == rhs.nullable))
      return false;
    if (__isset.autoIncrement != rhs.__isset.autoIncrement)
      return false;
    else if (__isset.autoIncrement && !(autoIncrement == rhs.autoIncrement))
      return false;
    if (__isset.parameterIn != rhs.__isset.parameterIn)
      return false;
    else if (__isset.parameterIn && !(parameterIn == rhs.parameterIn))
      return false;
    if (__isset.parameterOut != rhs.__isset.parameterOut)
      return false;
    else if (__isset.parameterOut && !(parameterOut == rhs.parameterOut))
      return false;
    if (__isset.elementTypes != rhs.__isset.elementTypes)
      return false;
    else if (__isset.elementTypes && !(elementTypes == rhs.elementTypes))
      return false;
    if (__isset.udtTypeAndClassName != rhs.__isset.udtTypeAndClassName)
      return false;
    else if (__isset.udtTypeAndClassName && !(udtTypeAndClassName == rhs.udtTypeAndClassName))
      return false;
    return true;
  }
  bool operator != (const ColumnDescriptor &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const ColumnDescriptor & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

  virtual void printTo(std::ostream& out) const;
};

void swap(ColumnDescriptor &a, ColumnDescriptor &b) noexcept;

inline std::ostream& operator<<(std::ostream& out, const ColumnDescriptor& obj)
{
  obj.printTo(out);
  return out;
}

}}} // namespace

#endif
