/**
 * Autogenerated by Thrift Compiler (0.10.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */

#ifndef SNAPPYDATA_STRUCT_BLOBCHUNK_H
#define SNAPPYDATA_STRUCT_BLOBCHUNK_H


#include "snappydata_struct_Decimal.h"

#include "snappydata_types.h"

namespace io { namespace snappydata { namespace thrift {

typedef struct _BlobChunk__isset {
  _BlobChunk__isset() : lobId(false), offset(false), totalLength(false) {}
  bool lobId :1;
  bool offset :1;
  bool totalLength :1;
} _BlobChunk__isset;

class BlobChunk {
 public:

  BlobChunk(const BlobChunk&);
  BlobChunk(BlobChunk&&) noexcept;
  BlobChunk& operator=(const BlobChunk&);
  BlobChunk& operator=(BlobChunk&&) noexcept;
  BlobChunk() : chunk(), last(0), lobId(0), offset(0), totalLength(0) {
  }

  virtual ~BlobChunk();
  std::string chunk;
  bool last;
  int64_t lobId;
  int64_t offset;
  int64_t totalLength;

  _BlobChunk__isset __isset;

  void __set_chunk(const std::string& val);

  void __set_last(const bool val);

  void __set_lobId(const int64_t val);

  void __set_offset(const int64_t val);

  void __set_totalLength(const int64_t val);

  bool operator == (const BlobChunk & rhs) const
  {
    if (!(chunk == rhs.chunk))
      return false;
    if (!(last == rhs.last))
      return false;
    if (__isset.lobId != rhs.__isset.lobId)
      return false;
    else if (__isset.lobId && !(lobId == rhs.lobId))
      return false;
    if (__isset.offset != rhs.__isset.offset)
      return false;
    else if (__isset.offset && !(offset == rhs.offset))
      return false;
    if (__isset.totalLength != rhs.__isset.totalLength)
      return false;
    else if (__isset.totalLength && !(totalLength == rhs.totalLength))
      return false;
    return true;
  }
  bool operator != (const BlobChunk &rhs) const {
    return !(*this == rhs);
  }

  bool operator < (const BlobChunk & ) const;

  uint32_t read(::apache::thrift::protocol::TProtocol* iprot);
  uint32_t write(::apache::thrift::protocol::TProtocol* oprot) const;

  virtual void printTo(std::ostream& out) const;
};

void swap(BlobChunk &a, BlobChunk &b);

inline std::ostream& operator<<(std::ostream& out, const BlobChunk& obj)
{
  obj.printTo(out);
  return out;
}

}}} // namespace

#endif
